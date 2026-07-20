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

import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.dao.ShoppingDao
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.sync.Hlc
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.sync.Stamper
import com.food.opencook.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory shopping store: enough of the DAO for the consolidation/provenance paths. */
private class InMemoryShoppingDao : ShoppingDao {
    val items = mutableListOf<ShoppingItemEntity>()
    override fun observeAll(): Flow<List<ShoppingItemEntity>> = throw NotImplementedError()
    override suspend fun getById(id: String): ShoppingItemEntity? = items.find { it.id == id }
    override suspend fun findOpenByText(text: String): ShoppingItemEntity? =
        items.find { !it.checked && it.text.equals(text, ignoreCase = true) }
    override suspend fun getChecked(): List<ShoppingItemEntity> = items.filter { it.checked }
    override suspend fun getAll(): List<ShoppingItemEntity> = items.toList()
    override suspend fun getBySource(recipeId: String, date: String): List<ShoppingItemEntity> =
        items.filter { it.sourceRecipeId == recipeId && it.sourceDate == date && !it.checked }
    override suspend fun getAllBySource(recipeId: String, date: String): List<ShoppingItemEntity> =
        items.filter { it.sourceRecipeId == recipeId && it.sourceDate == date }
    override suspend fun countBySource(recipeId: String, date: String): Int =
        getAllBySource(recipeId, date).size
    override suspend fun countOpenBySource(recipeId: String, date: String): Int =
        getBySource(recipeId, date).size
    override suspend fun getOpenByRecipe(recipeId: String): List<ShoppingItemEntity> =
        items.filter { it.sourceRecipeId == recipeId && !it.checked }
    override suspend fun distinctTexts(): List<String> = items.map { it.text }.distinct()
    override suspend fun upsert(item: ShoppingItemEntity) {
        items.removeAll { it.id == item.id }
        items += item
    }
    override suspend fun deleteById(id: String) { items.removeAll { it.id == id } }
}

private class NoopPantryDao : PantryDao {
    override fun observeAll(): Flow<List<PantryItemEntity>> = throw NotImplementedError()
    override suspend fun getById(id: String): PantryItemEntity? = null
    override suspend fun findByName(name: String): PantryItemEntity? = null
    override suspend fun allNames(): List<String> = emptyList()
    override suspend fun getAll(): List<PantryItemEntity> = emptyList()
    override suspend fun upsert(item: PantryItemEntity) {}
    override suspend fun deleteById(id: String) {}
}

private class NoopMessageDao : com.food.opencook.data.local.dao.MessageDao {
    override suspend fun insert(message: com.food.opencook.data.local.entity.MessageEntity) {}
    override suspend fun insertAll(messages: List<com.food.opencook.data.local.entity.MessageEntity>) {}
    override suspend fun maxTimestamp(dataset: String, rowId: String, column: String): String? = null
    override suspend fun maxTimestamp(): String? = null
    override suspend fun all(): List<com.food.opencook.data.local.entity.MessageEntity> = emptyList()
    override suspend fun since(cursor: String): List<com.food.opencook.data.local.entity.MessageEntity> = emptyList()
    override suspend fun forRow(dataset: String, rowId: String): List<com.food.opencook.data.local.entity.MessageEntity> = emptyList()
    override suspend fun existingTimestamps(timestamps: List<String>): List<String> = emptyList()
    override suspend fun count(): Int = 0
}

private class SeqStamper : Stamper {
    private var n = 0L
    override suspend fun stamp(): Hlc = Hlc(1_000 + n++, 0, "T")
}

class ShoppingConsolidationTest {

    private fun repo(dao: ShoppingDao): ShoppingRepository {
        val recorder = MessageRecorder(NoopMessageDao(), SeqStamper(), object : SyncTrigger {
            override fun requestSync() {}
        })
        return ShoppingRepository(dao, recorder, PantryRepository(NoopPantryDao(), recorder))
    }

    @Test
    fun `manual add sets the manual flag`() = runTest {
        val dao = InMemoryShoppingDao()
        repo(dao).addItem("Salz", manual = true)
        assertEquals(1, dao.items.size)
        assertTrue(dao.items.single().manual)
    }

    @Test
    fun `recipe add is not manual and records its source in the id set`() = runTest {
        val dao = InMemoryShoppingDao()
        repo(dao).addItem("Zwiebeln", quantity = 2.0, sourceRecipeId = "r1")
        val item = dao.items.single()
        assertFalse(item.manual)
        assertEquals("r1", item.sourceRecipeIds)
    }

    @Test
    fun `a manual touch latches manual on a consolidated line`() = runTest {
        val dao = InMemoryShoppingDao()
        val r = repo(dao)
        r.addItem("Nudeln", quantity = 400.0, unit = "g", sourceRecipeId = "r1") // recipe first
        r.addItem("Nudeln", quantity = 100.0, unit = "g", manual = true)          // then manual
        val item = dao.items.single()
        assertTrue(item.manual)
        assertEquals(500.0, item.quantity!!, 0.0001)
    }

    private fun recipe(id: String, vararg ingredients: Pair<String, Double>): RecipeWithDetails =
        RecipeWithDetails(
            recipe = RecipeEntity(id = id, name = "R-$id", createdAt = 0, updatedAt = 0),
            ingredients = ingredients.mapIndexed { i, (name, qty) ->
                IngredientEntity(id = "$id-$i", recipeId = id, position = i, quantity = qty, unit = "g", name = name)
            },
            instructions = emptyList(),
            images = emptyList(),
            nutrition = null,
        )

    @Test
    fun `addFromRecipe is idempotent per planned day`() = runTest {
        val dao = InMemoryShoppingDao()
        val r = repo(dao)
        val rec = recipe("r1", "Mehl" to 500.0)
        r.addFromRecipe(rec, sourceDate = "2026-06-15")
        r.addFromRecipe(rec, sourceDate = "2026-06-15") // second tap → no-op
        assertEquals(1, dao.items.size)
        assertEquals(500.0, dao.items.single().quantity!!, 0.0001)
    }

    @Test
    fun `addFromRecipe is idempotent for the recipe-screen add`() = runTest {
        val dao = InMemoryShoppingDao()
        val r = repo(dao)
        val rec = recipe("r1", "Mehl" to 500.0)
        r.addFromRecipe(rec) // no date (recipe screen)
        r.addFromRecipe(rec)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `consolidation collects every contributing recipe`() = runTest {
        val dao = InMemoryShoppingDao()
        val r = repo(dao)
        r.addItem("Zwiebeln", quantity = 1.0, sourceRecipeId = "r1")
        r.addItem("Zwiebeln", quantity = 2.0, sourceRecipeId = "r2")
        r.addItem("Zwiebeln", quantity = 1.0, sourceRecipeId = "r1") // dup id ignored
        val item = dao.items.single()
        assertEquals("r1,r2", item.sourceRecipeIds)
        assertEquals(4.0, item.quantity!!, 0.0001)
    }
}
