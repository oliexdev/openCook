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

import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.sync.FieldChange
import com.food.opencook.sync.RecipeMessageEncoder
import com.food.opencook.sync.SyncDatasets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMessageEncoderTest {

    @Test
    fun encodesRecipeIngredientAndNutritionFields() {
        val recipe = RecipeEntity(id = "r1", name = "Soup", servings = 2, category = "Suppe", createdAt = 0, updatedAt = 0)
        val ingredient = IngredientEntity("i1", "r1", 0, 400.0, "g", "Nudeln")
        val nutrition = NutritionEntity(recipeId = "r1", calories = "560 kcal")

        val changes = RecipeMessageEncoder.encode(recipe, listOf(ingredient), emptyList(), nutrition)

        fun value(dataset: String, row: String, col: String): String? =
            changes.firstOrNull { it.dataset == dataset && it.rowId == row && it.column == col }?.value

        // Strings are JSON-encoded; nulls are the literal "null"; booleans/ints bare.
        assertEquals("\"Soup\"", value(SyncDatasets.RECIPES, "r1", "name"))
        assertEquals("2", value(SyncDatasets.RECIPES, "r1", "servings"))
        assertEquals("\"Suppe\"", value(SyncDatasets.RECIPES, "r1", "category"))
        assertEquals("null", value(SyncDatasets.RECIPES, "r1", "prepTime"))
        assertEquals("false", value(SyncDatasets.RECIPES, "r1", SyncDatasets.COLUMN_DELETED))

        assertEquals("400.0", value(SyncDatasets.INGREDIENTS, "i1", "quantity"))
        assertEquals("\"g\"", value(SyncDatasets.INGREDIENTS, "i1", "unit"))
        assertEquals("\"Nudeln\"", value(SyncDatasets.INGREDIENTS, "i1", "name"))
        assertEquals("0", value(SyncDatasets.INGREDIENTS, "i1", "position"))
        assertEquals("\"r1\"", value(SyncDatasets.INGREDIENTS, "i1", "recipeId"))

        assertEquals("\"560 kcal\"", value(SyncDatasets.NUTRITION, "r1", "calories"))

        // Every change targets a non-empty dataset/row/column.
        assertTrue(changes.all { it.dataset.isNotBlank() && it.rowId.isNotBlank() && it.column.isNotBlank() })
    }
}
