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

import com.food.opencook.data.remote.dto.JobResponseDto
import com.food.opencook.data.remote.dto.RecipeDto
import com.food.opencook.data.remote.mapper.toMappedRecipe
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tolerant parser configured exactly like the app's NetworkModule. */
private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

class RecipeMapperTest {

    // A realistic server payload: schema.org array, structured ingredients,
    // nutrition present, an unknown extra key, and an ISO-8601 prepTime.
    private val serverJson = """
        {
          "job_id": "abc-123",
          "status": "done",
          "result": [
            {
              "@context": "https://schema.org",
              "@type": "Recipe",
              "name": "Test Rezept",
              "recipeYield": "2 Portionen",
              "openCookServings": 2,
              "openCookCategory": "Pasta",
              "recipeIngredient": ["400 g Nudeln", "1 Bund Basilikum"],
              "recipeInstructions": [{"@type": "HowToStep", "text": "Alles kochen."}],
              "image": ["crop-1.jpg"],
              "openCookIngredients": [
                {"quantity": 400, "unit": "g", "name": "Nudeln"},
                {"quantity": 1, "unit": "Bund", "name": "Basilikum"}
              ],
              "openCookNotes": ["Tipp"],
              "prepTime": "PT25M",
              "someFutureField": 42,
              "nutrition": {
                "@type": "NutritionInformation",
                "calories": "560 kcal",
                "proteinContent": "17 g",
                "openCookBasis": "pro Portion"
              }
            }
          ],
          "created_at": "2026-05-23T15:00:00Z",
          "updated_at": "2026-05-23T15:01:00Z"
        }
    """.trimIndent()

    @Test
    fun parsesTolerantlyAndMapsStructuredIngredients() {
        val response = json.decodeFromString<JobResponseDto>(serverJson)
        val dto = response.result!!.single()

        var counter = 0
        val mapped = dto.toMappedRecipe(sourcePhotoId = "photo-1", now = 1000L) { "id-${counter++}" }

        assertEquals("Test Rezept", mapped.recipe.name)
        assertEquals("2 Portionen", mapped.recipe.recipeYield)
        assertEquals(2, mapped.recipe.servings)
        assertEquals("pasta", mapped.recipe.category)
        assertEquals("PT25M", mapped.recipe.prepTime)
        assertEquals("photo-1", mapped.recipe.sourcePhotoId)

        // Structured ingredients win over the flattened strings.
        assertEquals(2, mapped.ingredients.size)
        assertEquals(400.0, mapped.ingredients[0].quantity!!, 0.001)
        assertEquals("g", mapped.ingredients[0].unit)
        assertEquals("Nudeln", mapped.ingredients[0].name)
        assertEquals(0, mapped.ingredients[0].position)

        assertEquals(1, mapped.instructions.size)
        assertEquals("Alles kochen.", mapped.instructions[0].text)

        assertEquals(1, mapped.images.size)
        assertEquals("crop-1.jpg", mapped.images[0].remoteName)
        assertTrue(mapped.images[0].isPrimary)

        assertEquals("560 kcal", mapped.nutrition!!.calories)
        assertEquals("17 g", mapped.nutrition!!.proteinContent)
        assertEquals("pro Portion", mapped.nutrition!!.basis)
    }

    @Test
    fun fallsBackToFlatIngredientsWhenNoStructured() {
        val dto = RecipeDto(
            name = "Flat",
            recipeIngredient = listOf("2 Eier", "Mehl"),
        )
        val mapped = dto.toMappedRecipe(sourcePhotoId = null, now = 0L)
        assertEquals(2, mapped.ingredients.size)
        assertEquals("2 Eier", mapped.ingredients[0].name)
        assertNull(mapped.ingredients[0].quantity)
    }

    @Test
    fun mergesExactDuplicateIngredientsWithSameUnit() {
        // Safety net behind the prompt: the same ingredient slipped in twice
        // (e.g. once from the list, once from the step text the prompt now ignores).
        val dto = RecipeDto(
            name = "Dupes",
            openCookIngredients = listOf(
                com.food.opencook.data.remote.dto.IngredientDto(1.0, null, "rote Paprikaschote"),
                com.food.opencook.data.remote.dto.IngredientDto(1.0, null, "Rote Paprikaschote"),
                com.food.opencook.data.remote.dto.IngredientDto(100.0, "g", "Erbsen"),
                com.food.opencook.data.remote.dto.IngredientDto(50.0, "g", " erbsen "),
            ),
        )
        val mapped = dto.toMappedRecipe(sourcePhotoId = null, now = 0L)

        // "rote Paprikaschote" x2 (no unit) collapse to one; Erbsen 100g + 50g = 150g.
        assertEquals(2, mapped.ingredients.size)
        assertEquals("rote Paprikaschote", mapped.ingredients[0].name)
        assertEquals(1.0, mapped.ingredients[0].quantity!!, 0.001)
        assertEquals("Erbsen", mapped.ingredients[1].name)
        assertEquals(150.0, mapped.ingredients[1].quantity!!, 0.001)
        // Positions are re-indexed after dedup.
        assertEquals(0, mapped.ingredients[0].position)
        assertEquals(1, mapped.ingredients[1].position)
    }

    @Test
    fun keepsSameNameWithDifferentUnitSeparate() {
        // Conservative: differing units never merge (don't corrupt amounts).
        val dto = RecipeDto(
            name = "Mixed units",
            openCookIngredients = listOf(
                com.food.opencook.data.remote.dto.IngredientDto(100.0, "g", "Erbsen"),
                com.food.opencook.data.remote.dto.IngredientDto(1.0, "Dose", "Erbsen"),
            ),
        )
        val mapped = dto.toMappedRecipe(sourcePhotoId = null, now = 0L)
        assertEquals(2, mapped.ingredients.size)
    }

    @Test
    fun stripsLeadingStepNumbersFromInstructions() {
        // Cookbook prints "1. ..." and the UI also numbers steps -> would show
        // "1. 1. ...". The leading printed number must be dropped on import.
        val dto = RecipeDto(
            name = "Numbered steps",
            recipeInstructions = listOf(
                com.food.opencook.data.remote.dto.HowToStepDto(text = "1. Backofen vorheizen."),
                com.food.opencook.data.remote.dto.HowToStepDto(text = "2)  Gemüse waschen"),
                com.food.opencook.data.remote.dto.HowToStepDto(text = "Alles 3-4 Min. braten."),
            ),
        )
        val mapped = dto.toMappedRecipe(sourcePhotoId = null, now = 0L)
        assertEquals("Backofen vorheizen.", mapped.instructions[0].text)
        assertEquals("Gemüse waschen", mapped.instructions[1].text)
        // A "3-4 Min." prefix is not a step number and stays intact.
        assertEquals("Alles 3-4 Min. braten.", mapped.instructions[2].text)
    }

    @Test
    fun dropsEmptyNutritionBlock() {
        val dto = RecipeDto(
            name = "No nutrition",
            nutrition = com.food.opencook.data.remote.dto.NutritionDto(calories = null),
        )
        val mapped = dto.toMappedRecipe(sourcePhotoId = null, now = 0L)
        assertNull(mapped.nutrition)
    }

    @Test
    fun handlesMissingOptionalFields() {
        val dto = json.decodeFromString<RecipeDto>("""{"@type":"Recipe","name":"Minimal"}""")
        assertEquals("Minimal", dto.name)
        assertTrue(dto.recipeIngredient.isEmpty())
        assertTrue(dto.recipeInstructions.isEmpty())
        assertNull(dto.nutrition)
        val mapped = dto.toMappedRecipe(sourcePhotoId = null, now = 0L)
        assertTrue(mapped.ingredients.isEmpty())
        assertFalse(mapped.images.any())
    }
}
