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

import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeExportTest {

    private fun recipe(
        name: String? = "Pasta Bolognese",
        notes: String? = null,
        tags: String? = null,
    ) = RecipeEntity(id = "r1", name = name, notes = notes, tags = tags, createdAt = 0, updatedAt = 0)

    @Test
    fun `maps core fields and marks the schema-org type`() {
        val data = RecipeWithDetails(
            recipe = recipe(),
            ingredients = emptyList(),
            instructions = emptyList(),
            images = emptyList(),
            nutrition = null,
        )

        val dto = RecipeExport.toDto(data)

        assertEquals("https://schema.org", dto.context)
        assertEquals("Recipe", dto.type)
        assertEquals("Pasta Bolognese", dto.name)
    }

    @Test
    fun `ingredients keep structured fields and a flattened text line, ordered by position`() {
        val data = RecipeWithDetails(
            recipe = recipe(),
            ingredients = listOf(
                IngredientEntity(id = "i2", recipeId = "r1", position = 1, quantity = null, unit = null, name = "Salt, to taste"),
                IngredientEntity(id = "i1", recipeId = "r1", position = 0, quantity = 400.0, unit = "g", name = "Pasta"),
            ),
            instructions = emptyList(),
            images = emptyList(),
            nutrition = null,
        )

        val dto = RecipeExport.toDto(data)

        // Position 0 first, whole-number quantity prints without a decimal point.
        assertEquals(listOf("400 g Pasta", "Salt, to taste"), dto.recipeIngredient)
        assertEquals(400.0, dto.openCookIngredients[0].quantity)
        assertEquals("g", dto.openCookIngredients[0].unit)
        assertNull(dto.openCookIngredients[1].quantity)
    }

    @Test
    fun `notes and tags split the stored newline-joined string back into lines`() {
        val data = RecipeWithDetails(
            recipe = recipe(notes = "Great with garlic bread\nFreezes well", tags = "quick\nvegetarian"),
            ingredients = emptyList(),
            instructions = emptyList(),
            images = emptyList(),
            nutrition = null,
        )

        val dto = RecipeExport.toDto(data)

        assertEquals(listOf("Great with garlic bread", "Freezes well"), dto.openCookNotes)
        assertEquals(listOf("quick", "vegetarian"), dto.openCookTags)
    }

    @Test
    fun `instructions are ordered by position regardless of input order`() {
        val data = RecipeWithDetails(
            recipe = recipe(),
            ingredients = emptyList(),
            instructions = listOf(
                InstructionEntity(id = "s2", recipeId = "r1", position = 1, text = "Simmer 20 minutes"),
                InstructionEntity(id = "s1", recipeId = "r1", position = 0, text = "Brown the mince"),
            ),
            images = emptyList(),
            nutrition = null,
        )

        val dto = RecipeExport.toDto(data)

        assertEquals(listOf("Brown the mince", "Simmer 20 minutes"), dto.recipeInstructions.map { it.text })
        assertTrue(dto.recipeInstructions.all { it.type == "HowToStep" })
    }

    @Test
    fun `nutrition maps straight across and basis becomes openCookBasis`() {
        val data = RecipeWithDetails(
            recipe = recipe(),
            ingredients = emptyList(),
            instructions = emptyList(),
            images = emptyList(),
            nutrition = NutritionEntity(recipeId = "r1", calories = "560 kcal", basis = "pro Portion"),
        )

        val dto = RecipeExport.toDto(data)

        assertEquals("560 kcal", dto.nutrition?.calories)
        assertEquals("pro Portion", dto.nutrition?.openCookBasis)
    }

    @Test
    fun `no nutrition row means no nutrition in the export`() {
        val data = RecipeWithDetails(
            recipe = recipe(),
            ingredients = emptyList(),
            instructions = emptyList(),
            images = emptyList(),
            nutrition = null,
        )

        assertNull(RecipeExport.toDto(data).nutrition)
    }
}
