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

package com.food.opencook

import com.food.opencook.data.backup.RecipeDtoEncoder
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.recipeimport.RecipeImportParser
import com.food.opencook.data.remote.dto.RecipeDto
import com.food.opencook.data.remote.mapper.toMappedRecipe
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup's correctness rests on one thing: a recipe written to `recipes.json` and
 * read back must be the same recipe, with the same row ids. Anything lost here shows up
 * as a duplicate or a mangled amount after a restore, so this exercises the full
 * entity → schema.org → entity path the way [BackupWriter]/[BackupImporter] use it.
 */
class BackupRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private fun sample() = RecipeWithDetails(
        recipe = RecipeEntity(
            id = "recipe-1",
            name = "Pfannkuchen",
            description = "Dünn und golden.",
            recipeYield = "4 Portionen",
            servings = 4,
            category = "dessert",
            notes = "Teig 30 Min ruhen lassen\nMit Zucker servieren",
            tags = "süß\nschnell",
            lastCookedAt = "2026-07-01",
            cookbook = "Omas Küche",
            prepTime = "PT10M",
            cookTime = "PT20M",
            totalTime = "PT30M",
            sourcePhotoId = null,
            householdId = null,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_500_000,
        ),
        ingredients = listOf(
            IngredientEntity("ing-1", "recipe-1", 0, 250.0, "g", "Mehl"),
            IngredientEntity("ing-2", "recipe-1", 1, 3.0, null, "Eier"),
            IngredientEntity("ing-3", "recipe-1", 2, null, null, "Salz"),
        ),
        instructions = listOf(
            InstructionEntity("step-1", "recipe-1", 0, "Alles verrühren."),
            InstructionEntity("step-2", "recipe-1", 1, "In der Pfanne backen."),
        ),
        images = listOf(ImageEntity("img-1", "recipe-1", 0, null, "/tmp/x.jpg", true)),
        nutrition = NutritionEntity("recipe-1", "560 kcal", "17 g", "22 g", "70 g", null, "3 g", "pro Portion"),
    )

    /** Encode → JSON → parse → map, exactly as export and restore do. */
    private fun roundTrip(details: RecipeWithDetails, imageRef: String? = "images/recipe-1.jpg"): RecipeWithDetails {
        val dto = RecipeDtoEncoder.encode(details, imageRef)
        val text = json.encodeToString(RecipeDto.serializer(), dto)
        val parsed = RecipeImportParser.parse(text, json)
        assertEquals(1, parsed.size)
        val mapped = parsed[0].copy(image = emptyList()).toMappedRecipe(sourcePhotoId = null, now = 0)
        return RecipeWithDetails(mapped.recipe, mapped.ingredients, mapped.instructions, emptyList(), mapped.nutrition)
    }

    @Test
    fun rowIdsSurviveSoRestoreUpsertsInsteadOfDuplicating() {
        val out = roundTrip(sample())
        assertEquals("recipe-1", out.recipe.id)
        assertEquals(listOf("ing-1", "ing-2", "ing-3"), out.ingredients.map { it.id })
        assertEquals(listOf("step-1", "step-2"), out.instructions.map { it.id })
    }

    @Test
    fun structuredAmountsAreNotReParsedFromText() {
        val out = roundTrip(sample())
        assertEquals(listOf(250.0, 3.0, null), out.ingredients.map { it.quantity })
        assertEquals(listOf("g", null, null), out.ingredients.map { it.unit })
        assertEquals(listOf("Mehl", "Eier", "Salz"), out.ingredients.map { it.name })
    }

    @Test
    fun recipeFieldsSurvive() {
        val original = sample().recipe
        val out = roundTrip(sample()).recipe
        assertEquals(original.name, out.name)
        assertEquals(original.description, out.description)
        assertEquals(original.recipeYield, out.recipeYield)
        assertEquals(original.servings, out.servings)
        assertEquals(original.category, out.category)
        assertEquals(original.notes, out.notes)
        assertEquals(original.tags, out.tags)
        assertEquals(original.cookbook, out.cookbook)
        assertEquals(original.lastCookedAt, out.lastCookedAt)
        assertEquals(original.prepTime, out.prepTime)
        assertEquals(original.cookTime, out.cookTime)
        assertEquals(original.totalTime, out.totalTime)
        // Timestamps come back so "recently added" ordering survives a restore.
        assertEquals(original.createdAt, out.createdAt)
        assertEquals(original.updatedAt, out.updatedAt)
    }

    @Test
    fun nutritionSurvives() {
        val out = roundTrip(sample()).nutrition
        assertEquals("560 kcal", out?.calories)
        assertEquals("17 g", out?.proteinContent)
        assertEquals("70 g", out?.carbohydrateContent)
        assertNull(out?.fiberContent)
        assertEquals("pro Portion", out?.basis)
    }

    @Test
    fun aSingleStepIsNotSentenceSplit() {
        // The importer splits a one-blob instruction into sentences for foreign bundles.
        // Our own backups carry step ids and must survive unsplit.
        val one = sample().let {
            it.copy(instructions = listOf(InstructionEntity("step-1", "recipe-1", 0, "Verrühren. Backen. Servieren.")))
        }
        val out = roundTrip(one)
        assertEquals(1, out.instructions.size)
        assertEquals("Verrühren. Backen. Servieren.", out.instructions[0].text)
    }

    @Test
    fun flattenedLinesStayReadableForOtherTools() {
        val dto = RecipeDtoEncoder.encode(sample(), null)
        // No stray ".0" — an exported backup should read like a recipe.
        assertEquals(listOf("250 g Mehl", "3 Eier", "Salz"), dto.recipeIngredient)
    }

    @Test
    fun aForeignRecipeWithoutIdsStillGetsFreshOnes() {
        val parsed = RecipeImportParser.parse(
            """{"name":"Fremd","recipeIngredient":["200 g Mehl"],"recipeInstructions":["Rühren."]}""",
            json,
        )
        val mapped = parsed[0].toMappedRecipe(sourcePhotoId = null, now = 0)
        assertTrue(mapped.recipe.id.isNotBlank())
        assertNotEquals("recipe-1", mapped.recipe.id)
        assertEquals(1, mapped.ingredients.size)
        assertEquals(200.0, mapped.ingredients[0].quantity)
    }
}
