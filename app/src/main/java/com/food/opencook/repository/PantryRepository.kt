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

package com.food.opencook.repository

import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.sync.PantryMessageEncoder
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.IngredientStaples
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Pantry / staples — what's in stock, so the shopping list can skip it. Syncs. */
@Singleton
class PantryRepository @Inject constructor(
    private val pantryDao: PantryDao,
    private val messageRecorder: MessageRecorder,
) {
    fun observeItems(): Flow<List<PantryItemEntity>> = pantryDao.observeAll()

    /** Lower-cased staple names, for skipping owned ingredients. */
    suspend fun stockedNames(): Set<String> = pantryDao.allNames().map { it.lowercase().trim() }.toSet()

    suspend fun addItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || pantryDao.findByName(trimmed) != null) return
        val now = System.currentTimeMillis()
        val item = PantryItemEntity(UUID.randomUUID().toString(), trimmed, now, now)
        pantryDao.upsert(item)
        messageRecorder.record(PantryMessageEncoder.encode(item))
    }

    suspend fun deleteItem(id: String) {
        pantryDao.deleteById(id)
        messageRecorder.record(PantryMessageEncoder.tombstone(id))
    }

    /** Restore pantry items from a backup — see [ShoppingRepository.importItems]. */
    suspend fun importItems(items: List<PantryItemEntity>) {
        if (items.isEmpty()) return
        items.forEach { pantryDao.upsert(it) }
        messageRecorder.record(items.flatMap { PantryMessageEncoder.encode(it) })
    }

    /**
     * "Out" half of the pantry cycle: cooking a dish consumes its perishables. Staples
     * ([IngredientStaples]) are kept — a packet of flour isn't used up by one bake, and
     * without amounts we can't track partial use. Binary: a matched item is removed whole.
     * Un-cooking does not restore items (not reconstructable without amounts).
     */
    suspend fun consume(ingredientNames: List<String>) {
        val pantry = pantryDao.getAll()
        if (pantry.isEmpty()) return
        ingredientNames.map { it.trim() }
            .filter { it.isNotEmpty() && !IngredientStaples.isStaple(it) }
            .forEach { ing -> pantry.firstOrNull { IngredientMatch.covers(it.name, ing) }?.let { deleteItem(it.id) } }
    }

    /**
     * Pre-fill the pantry with a curated basics list ([IngredientStaples.DEFAULT_PANTRY]) so
     * a fresh household doesn't start empty. Idempotent: plural-aware against existing
     * items (so seeding twice — or seeding after a partial manual setup — never produces
     * duplicates like "Salz" + "salz"). Called once from the household-creator path in
     * onboarding; joiners receive the pantry through the regular sync pull instead.
     */
    suspend fun seedDefaults() {
        val existing = pantryDao.allNames()
        IngredientStaples.DEFAULT_PANTRY.forEach { item ->
            if (!IngredientMatch.containsLike(existing, item)) addItem(item)
        }
    }
}
