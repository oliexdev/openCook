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

import com.food.opencook.data.local.dao.GroceryOverrideDao
import com.food.opencook.data.local.entity.GroceryOverrideEntity
import com.food.opencook.sync.GroceryOverrideMessageEncoder
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.util.GroceryCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The household's learned "item name → grocery aisle" corrections. Written when a
 * shopping/pantry item is dragged into another category group; consulted (via the
 * observed map) before the keyword heuristic. Syncs — one person's correction
 * teaches every device.
 */
@Singleton
class GroceryOverrideRepository @Inject constructor(
    private val dao: GroceryOverrideDao,
    private val messageRecorder: MessageRecorder,
) {

    /** Normalized-name → category, invalid rows (unknown enum values) dropped. */
    fun observeOverrides(): Flow<Map<String, GroceryCategory>> =
        dao.observeAll().map { rows ->
            rows.mapNotNull { row ->
                row.category.toCategoryOrNull()?.let { normalize(row.name) to it }
            }.toMap()
        }

    /** Remember "this name belongs in that aisle" and sync the lesson. */
    suspend fun learn(name: String, category: GroceryCategory) {
        val key = normalize(name)
        if (key.isEmpty()) return
        val entity = GroceryOverrideEntity(key, category.name, System.currentTimeMillis())
        dao.upsert(entity)
        messageRecorder.record(GroceryOverrideMessageEncoder.encode(entity))
    }

    /** Restore lessons from a backup — additive + re-recorded, like the other importItems. */
    suspend fun importItems(items: List<GroceryOverrideEntity>) {
        val valid = items
            .map { it.copy(name = normalize(it.name)) }
            .filter { it.name.isNotEmpty() && it.category.toCategoryOrNull() != null }
        if (valid.isEmpty()) return
        valid.forEach { dao.upsert(it) }
        messageRecorder.record(valid.flatMap { GroceryOverrideMessageEncoder.encode(it) })
    }

    suspend fun all(): List<GroceryOverrideEntity> = dao.getAll()

    private fun normalize(name: String) = name.trim().lowercase()

    private fun String.toCategoryOrNull(): GroceryCategory? =
        GroceryCategory.entries.firstOrNull { it.name == this }
}
