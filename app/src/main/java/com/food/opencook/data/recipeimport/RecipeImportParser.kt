package com.food.opencook.data.recipeimport

import com.food.opencook.data.remote.dto.HowToStepDto
import com.food.opencook.data.remote.dto.NutritionDto
import com.food.opencook.data.remote.dto.RecipeDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses imported recipe JSON into [RecipeDto]s (which the existing mapper turns into
 * Room entities). Tolerant by design so it accepts the de-facto interchange standard —
 * schema.org/Recipe (JSON-LD) as exported by recipe apps or embedded in recipe pages —
 * as well as simpler shapes:
 *
 *  - a single recipe object, an array of objects, a `{ "recipes": [...] }` wrapper, or a
 *    JSON-LD document with `@graph` (non-recipe nodes are dropped by the validity check);
 *  - field names in either schema.org (`recipeIngredient`/`recipeInstructions`) or plain
 *    (`Ingredients`/`Instructions`/`name`) form;
 *  - instructions as a single string, a list of strings, `{text}` steps, or `HowToSection`s;
 *  - ingredients as strings or objects.
 *
 * A recipe needs a non-blank name and at least one ingredient or step; anything else is
 * skipped. The `image` reference(s) are kept and resolved by the import flow in one of
 * three forms: a `data:`-URI (embedded), a relative path (inside a .zip bundle), or an
 * http(s) URL (fetched best-effort; skipped if offline/unreachable). Source URLs are ignored.
 */
object RecipeImportParser {

    /** Parse [jsonText]; returns the valid recipes found (invalid/foreign entries dropped). */
    fun parse(jsonText: String, json: Json): List<RecipeDto> {
        val root = json.parseToJsonElement(jsonText)
        return collectRecipeObjects(root).mapNotNull { toRecipeDto(it) }
    }

    private fun collectRecipeObjects(element: JsonElement): List<JsonObject> = when (element) {
        is JsonArray -> element.flatMap { collectRecipeObjects(it) }
        is JsonObject -> when {
            element.containsKey("@graph") ->
                (element["@graph"] as? JsonArray)?.flatMap { collectRecipeObjects(it) }.orEmpty()
            element["recipes"] is JsonArray ->
                (element["recipes"] as JsonArray).flatMap { collectRecipeObjects(it) }
            else -> listOf(element)
        }
        else -> emptyList()
    }

    private fun toRecipeDto(obj: JsonObject): RecipeDto? {
        val name = obj.firstString("name", "Name", "headline", "title")?.trim()
        val ingredientLines = extractIngredients(obj)
        val instructions = extractInstructions(obj)
        if (name.isNullOrBlank() || (ingredientLines.isEmpty() && instructions.isEmpty())) return null

        // Parse free-text ingredient lines into structured quantity/unit/name so the
        // imported recipe can be scaled like any other (the mapper prefers these).
        val structured = ingredientLines.map { IngredientLineParser.parse(it) }

        val yieldStr = obj.firstString("recipeYield", "yield", "Portionen", "servings")
        return RecipeDto(
            name = name,
            recipeYield = yieldStr,
            openCookServings = yieldStr?.let(::leadingInt),
            openCookIngredients = structured,
            recipeInstructions = instructions,
            image = extractImages(obj), // refs resolved by the import flow (data-URI / zip path / http)
            openCookTags = extractTags(obj),
            prepTime = obj.firstString("prepTime")?.takeIf { it.startsWith("PT") },
            cookTime = obj.firstString("cookTime")?.takeIf { it.startsWith("PT") },
            totalTime = obj.firstString("totalTime")?.takeIf { it.startsWith("PT") },
            nutrition = extractNutrition(obj),
            cookbook = extractCookbook(obj),
        )
    }

    /** Source cookbook from schema.org `isPartOf` (a Book/CreativeWork) — its `name`,
     *  or a bare string. schema.org/Recipe has no dedicated cookbook property, so this
     *  is the standard way to express it; openCook stores the name in its `cookbook` column. */
    private fun extractCookbook(obj: JsonObject): String? =
        when (val part = obj.first("isPartOf")) {
            is JsonObject -> part.firstString("name")
            is JsonPrimitive -> part.str()
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractIngredients(obj: JsonObject): List<String> {
        val el = obj.first("recipeIngredient", "ingredients", "Ingredients", "zutaten") ?: return emptyList()
        return when (el) {
            is JsonArray -> el.mapNotNull { ingredientToString(it) }
            is JsonPrimitive -> el.str().toLines()
            else -> emptyList()
        }
    }

    private fun ingredientToString(el: JsonElement): String? = when (el) {
        is JsonPrimitive -> el.str()?.trim()?.takeIf { it.isNotEmpty() }
        is JsonObject -> el.firstString("text", "name", "ingredient")
            ?: listOfNotNull(
                el.firstString("amount", "quantity"),
                el.firstString("unit"),
                el.firstString("food", "ingredient"),
            ).joinToString(" ").trim().takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun extractInstructions(obj: JsonObject): List<HowToStepDto> {
        val el = obj.first("recipeInstructions", "instructions", "Instructions", "zubereitung")
            ?: return emptyList()
        val steps = mutableListOf<String>()
        fun add(e: JsonElement) {
            when (e) {
                is JsonPrimitive -> e.str()?.toLines()?.forEach(steps::add)
                is JsonArray -> e.forEach(::add)
                is JsonObject -> {
                    val type = e.firstString("@type").orEmpty()
                    if (type.contains("HowToSection", ignoreCase = true)) {
                        e.first("itemListElement")?.let(::add)
                    } else {
                        val text = e.firstString("text", "name", "step", "description")
                        if (text != null) steps.add(text) else e.first("itemListElement")?.let(::add)
                    }
                }
                else -> {}
            }
        }
        add(el)
        // A single blob (typical of plain imports) → split into sentences; already-stepped
        // lists are left as-is so well-structured imports aren't over-fragmented.
        val finalSteps = if (steps.size <= 1) splitIntoSentences(steps.firstOrNull().orEmpty()) else steps
        return finalSteps.map { HowToStepDto(text = it.trim()) }.filter { it.text.isNotEmpty() }
    }

    /** Split one instruction paragraph into sentence-steps; tolerant of a missing space
     * after the period ("…schneiden.Den…"). */
    private fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val spaced = text.replace(Regex("""([a-zäöüß0-9])([.!?])([A-ZÄÖÜ])"""), "$1$2 $3")
        return spaced.split(Regex("""(?<=[.!?])\s+(?=[A-ZÄÖÜ])"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(text.trim()) }
    }

    private fun extractTags(obj: JsonObject): List<String> {
        val el = obj.first("keywords", "recipeCategory", "recipeCuisine") ?: return emptyList()
        return when (el) {
            is JsonArray -> el.mapNotNull { it.str()?.trim() }.filter { it.isNotEmpty() }
            is JsonPrimitive -> el.str()?.split(",")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            else -> emptyList()
        }
    }

    /** Image reference(s): schema.org `image` as a string, an array, or an ImageObject
     *  ({url}/{contentUrl}). Returned verbatim (data-URI / relative path / http URL); the
     *  import flow decides how to resolve each. */
    private fun extractImages(obj: JsonObject): List<String> {
        fun refs(e: JsonElement): List<String> = when (e) {
            is JsonArray -> e.flatMap { refs(it) }
            is JsonObject -> listOfNotNull(e.firstString("url", "contentUrl", "@id"))
            is JsonPrimitive -> listOfNotNull(e.str()?.trim()?.takeIf { it.isNotEmpty() })
            else -> emptyList()
        }
        return (obj.first("image", "images", "photo") ?: return emptyList()).let(::refs)
    }

    private fun extractNutrition(obj: JsonObject): NutritionDto? {
        val n = obj.first("nutrition") as? JsonObject ?: return null
        val dto = NutritionDto(
            calories = n.firstString("calories"),
            proteinContent = n.firstString("proteinContent"),
            fatContent = n.firstString("fatContent"),
            carbohydrateContent = n.firstString("carbohydrateContent"),
            fiberContent = n.firstString("fiberContent"),
            sugarContent = n.firstString("sugarContent"),
            openCookBasis = n.firstString("servingSize"),
        )
        val hasValue = listOf(
            dto.calories, dto.proteinContent, dto.fatContent,
            dto.carbohydrateContent, dto.fiberContent, dto.sugarContent,
        ).any { !it.isNullOrBlank() }
        return if (hasValue) dto else null
    }

    // --- small helpers ---

    private fun JsonObject.first(vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { this[it]?.takeUnless { e -> e is JsonNull } }

    private fun JsonObject.firstString(vararg keys: String): String? = first(*keys)?.str()

    private fun JsonElement.str(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun String?.toLines(): List<String> =
        this?.split("\n")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

    /** First integer at the start of e.g. "4 Portionen" → 4; null if none. */
    private fun leadingInt(s: String): Int? =
        Regex("""^\s*(\d{1,3})""").find(s)?.groupValues?.get(1)?.toIntOrNull()
}
