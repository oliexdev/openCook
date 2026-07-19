/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook.util

import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.remote.dto.HowToStepDto
import com.food.opencook.data.remote.dto.IngredientDto
import com.food.opencook.data.remote.dto.NutritionDto
import com.food.opencook.data.remote.dto.RecipeDto
import kotlinx.serialization.Serializable

/**
 * Turns a saved recipe back into the same schema.org/Recipe + openCook-extension shape
 * [com.food.opencook.data.recipeimport.RecipeImportParser] reads on the way in — so an
 * exported recipe round-trips: re-importing the file this produces (into openCook again,
 * a household member's device, or any other app that speaks schema.org/Recipe) recreates
 * the recipe with its structured ingredients intact, not just a flattened text blob.
 *
 * Images are deliberately **not** included: [com.food.opencook.data.local.entity.ImageEntity]
 * holds either a device-local file path or a bare filename that only resolves against this
 * household's own self-hosted server — neither is portable to whoever opens the exported
 * file, so emitting one would be actively misleading rather than merely incomplete.
 */
object RecipeExport {

    fun toDto(data: RecipeWithDetails): RecipeDto {
        val recipe = data.recipe
        return RecipeDto(
            context = "https://schema.org",
            type = "Recipe",
            name = recipe.name,
            recipeYield = recipe.recipeYield,
            openCookServings = recipe.servings,
            openCookCategory = recipe.category,
            recipeIngredient = data.ingredients.sortedBy { it.position }.map { ingredientLine(it.quantity, it.unit, it.name) },
            recipeInstructions = data.instructions.sortedBy { it.position }
                .map { HowToStepDto(type = "HowToStep", text = it.text) },
            openCookIngredients = data.ingredients.sortedBy { it.position }
                .map { IngredientDto(quantity = it.quantity, unit = it.unit, name = it.name) },
            openCookNotes = recipe.notes.orEmpty().lines().map(String::trim).filter(String::isNotEmpty),
            openCookTags = recipe.tags.orEmpty().lines().map(String::trim).filter(String::isNotEmpty),
            prepTime = recipe.prepTime,
            cookTime = recipe.cookTime,
            totalTime = recipe.totalTime,
            nutrition = data.nutrition?.let {
                NutritionDto(
                    type = "NutritionInformation",
                    calories = it.calories,
                    proteinContent = it.proteinContent,
                    fatContent = it.fatContent,
                    carbohydrateContent = it.carbohydrateContent,
                    fiberContent = it.fiberContent,
                    sugarContent = it.sugarContent,
                    openCookBasis = it.basis,
                )
            },
            cookbook = recipe.cookbook,
        )
    }

    /** Generates a human-readable Markdown version of the recipe. */
    fun toMarkdown(data: RecipeWithDetails): String {
        val r = data.recipe
        val sb = StringBuilder()
        sb.append("# ${r.name ?: "Unbenanntes Rezept"}\n\n")
        if (!r.cookbook.isNullOrBlank()) sb.append("**Kochbuch:** ${r.cookbook}\n")
        if (!r.category.isNullOrBlank()) sb.append("**Kategorie:** ${r.category}\n")

        val servings = r.servings?.toString() ?: r.recipeYield
        if (servings != null) sb.append("**Portionen:** $servings\n")

        val times = listOfNotNull(
            r.prepTime?.let { "Vorbereitung: ${DurationFormat.toHuman(it)}" },
            r.cookTime?.let { "Zubereitung: ${DurationFormat.toHuman(it)}" }
        ).joinToString(" · ")
        if (times.isNotEmpty()) sb.append("**Zeit:** $times\n")

        sb.append("\n## Zutaten\n\n")
        data.ingredients.sortedBy { it.position }.forEach { ing ->
            sb.append("- ${ingredientLine(ing.quantity, ing.unit, ing.name)}\n")
        }

        sb.append("\n## Zubereitung\n\n")
        data.instructions.sortedBy { it.position }.forEachIndexed { i, step ->
            sb.append("${i + 1}. ${step.text}\n")
        }

        if (!r.notes.isNullOrBlank()) {
            sb.append("\n## Notizen\n\n${r.notes}\n")
        }

        val n = data.nutrition
        if (n != null) {
            sb.append("\n## Nährwerte\n\n")
            if (!n.basis.isNullOrBlank()) sb.append("*${n.basis}*\n\n")
            listOfNotNull(
                n.calories?.let { "Kalorien: $it" },
                n.proteinContent?.let { "Eiweiß: $it" },
                n.fatContent?.let { "Fett: $it" },
                n.carbohydrateContent?.let { "Kohlenhydrate: $it" }
            ).forEach { sb.append("- $it\n") }
        }

        if (!r.tags.isNullOrBlank()) {
            sb.append("\n---\n*Tags: ${r.tags.replace("\n", ", ")}*")
        }

        return sb.toString()
    }

    /** "400 g Nudeln" / "3 Eier" / "etwas Salz" — the flattened text line kept alongside the
     *  structured fields, for any importer that only understands plain recipeIngredient text. */
    private fun ingredientLine(quantity: Double?, unit: String?, name: String): String =
        listOfNotNull(quantity?.let(::formatQuantity), unit, name).joinToString(" ")

    /** Room stores quantity as Double; print "400" rather than "400.0" for whole numbers. */
    private fun formatQuantity(quantity: Double): String =
        if (quantity == quantity.toLong().toDouble()) quantity.toLong().toString() else quantity.toString()
}

/** Comprehensive export of all household data. */
@Serializable
data class BulkExportDto(
    val recipes: List<RecipeDto> = emptyList(),
    val mealPlan: List<MealPlanExportDto> = emptyList(),
    val mealDays: List<MealDayExportDto> = emptyList(),
    val shoppingList: List<ShoppingItemExportDto> = emptyList(),
    val pantry: List<PantryItemExportDto> = emptyList(),
)

@Serializable
data class MealPlanExportDto(
    val date: String,
    val slot: String = "dinner",
    val recipeId: String,
    val pinned: Boolean = false,
    val reasonsJson: String? = null,
    val cookedAt: String? = null,
)

@Serializable
data class MealDayExportDto(
    val date: String,
    val skipped: Boolean = false,
)

@Serializable
data class ShoppingItemExportDto(
    val text: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val checked: Boolean = false,
    val sourceRecipeId: String? = null,
    val sourceDate: String? = null,
    val manual: Boolean = false,
)

@Serializable
data class PantryItemExportDto(
    val name: String,
)

