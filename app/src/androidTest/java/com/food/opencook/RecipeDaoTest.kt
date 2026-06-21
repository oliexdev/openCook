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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.food.opencook.data.local.OpenCookDatabase
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeDaoTest {

    private lateinit var db: OpenCookDatabase
    private lateinit var dao: RecipeDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenCookDatabase::class.java,
        ).build()
        dao = db.recipeDao()
    }

    @After
    fun tearDown() = db.close()

    private fun recipe(id: String) = RecipeEntity(
        id = id, name = "Test", createdAt = 1, updatedAt = 1,
    )

    private fun count(table: String): Int =
        db.query("SELECT COUNT(*) FROM $table", arrayOf()).use {
            it.moveToFirst(); it.getInt(0)
        }

    @Test
    fun insertsAndReadsRelations() = runBlocking {
        dao.insertRecipe(recipe("r1"))
        dao.insertIngredients(listOf(IngredientEntity("i1", "r1", 0, "400 g", null, "Nudeln")))
        dao.insertInstructions(listOf(InstructionEntity("s1", "r1", 0, "Kochen")))
        dao.insertNutrition(NutritionEntity(recipeId = "r1", calories = "560 kcal"))
        dao.insertImages(listOf(ImageEntity("img1", "r1", 0, "crop.jpg", null, true)))

        val loaded = dao.observeById("r1").first()!!
        assertEquals("Test", loaded.recipe.name)
        assertEquals("Nudeln", loaded.ingredients.single().name)
        assertEquals("Kochen", loaded.instructions.single().text)
        assertEquals("560 kcal", loaded.nutrition!!.calories)
        assertEquals("crop.jpg", loaded.images.single().remoteName)
    }

    @Test
    fun deletingRecipeCascadesToChildren() = runBlocking {
        dao.insertRecipe(recipe("r1"))
        dao.insertIngredients(listOf(IngredientEntity("i1", "r1", 0, null, null, "X")))
        dao.insertNutrition(NutritionEntity(recipeId = "r1", calories = "1 kcal"))

        dao.deleteRecipe("r1")

        assertNull(dao.observeById("r1").first())
        assertEquals(0, count("ingredients"))
        assertEquals(0, count("nutrition"))
    }

    @Test
    fun upsertReplacesChildRows() = runBlocking {
        dao.upsertRecipe(
            recipe("r1"),
            ingredients = listOf(
                IngredientEntity("i1", "r1", 0, null, null, "A"),
                IngredientEntity("i2", "r1", 1, null, null, "B"),
            ),
            instructions = emptyList(),
            nutrition = null,
        )
        assertEquals(2, count("ingredients"))

        // Re-save with a single (renamed) ingredient — old rows must be replaced.
        dao.upsertRecipe(
            recipe("r1"),
            ingredients = listOf(IngredientEntity("i3", "r1", 0, null, null, "C")),
            instructions = emptyList(),
            nutrition = null,
        )
        assertEquals(1, count("ingredients"))
        assertEquals("C", dao.observeById("r1").first()!!.ingredients.single().name)
    }
}
