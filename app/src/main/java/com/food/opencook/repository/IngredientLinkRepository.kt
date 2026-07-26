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

import com.food.opencook.data.local.dao.IngredientLinkDao
import com.food.opencook.data.local.entity.IngredientLinkEntity
import com.food.opencook.sync.IngredientLinkMessageEncoder
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.util.IngredientMatch
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The household's learned ingredient relationships (today: "these two are NOT the same
 * product"). Written when someone taps "brauch ich doch" on a wrongly-skipped shopping
 * item; consulted by [IngredientMatch] via [com.food.opencook.util.LearnedIngredientLinks].
 * Syncs — one person's lesson teaches every device. Mirrors [GroceryOverrideRepository].
 */
@Singleton
class IngredientLinkRepository @Inject constructor(
    private val dao: IngredientLinkDao,
    private val messageRecorder: MessageRecorder,
) {

    fun observeLinks(): Flow<List<IngredientLinkEntity>> = dao.observeAll()

    suspend fun all(): List<IngredientLinkEntity> = dao.getAll()

    /** Remember "[a] and [b] are not the same product" and sync the lesson. */
    suspend fun learnDistinct(a: String, b: String) {
        val entity = pair(a, b, "distinct") ?: return
        dao.upsert(entity)
        messageRecorder.record(IngredientLinkMessageEncoder.encode(entity))
    }

    /** Forget a lesson (falls back to the string rules everywhere). */
    suspend fun forget(a: String, b: String) {
        val (na, nb) = sortedNormalized(a, b) ?: return
        dao.deleteByPair(na, nb)
        messageRecorder.record(IngredientLinkMessageEncoder.tombstone(na, nb))
    }

    /** Restore lessons from a backup — additive + re-recorded, like the other importItems. */
    suspend fun importItems(items: List<IngredientLinkEntity>) {
        val valid = items.mapNotNull { pair(it.nameA, it.nameB, it.kind) }
        if (valid.isEmpty()) return
        valid.forEach { dao.upsert(it) }
        messageRecorder.record(valid.flatMap { IngredientLinkMessageEncoder.encode(it) })
    }

    /** Normalize + sort a name pair into an entity; null if either is blank or they collapse. */
    private fun pair(a: String, b: String, kind: String): IngredientLinkEntity? {
        val (na, nb) = sortedNormalized(a, b) ?: return null
        return IngredientLinkEntity(na, nb, kind, System.currentTimeMillis())
    }

    private fun sortedNormalized(a: String, b: String): Pair<String, String>? {
        val na = IngredientMatch.normalizeName(a)
        val nb = IngredientMatch.normalizeName(b)
        if (na.isEmpty() || nb.isEmpty() || na == nb) return null
        return if (na <= nb) na to nb else nb to na
    }
}
