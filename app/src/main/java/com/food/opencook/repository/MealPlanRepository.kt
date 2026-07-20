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

import com.food.opencook.data.local.dao.MealDayDao
import com.food.opencook.data.local.dao.MealPlanDao
import com.food.opencook.data.local.entity.MealDayEntity
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.sync.MealDayMessageEncoder
import com.food.opencook.sync.MealPlanMessageEncoder
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.ui.mealplan.MealPlanner.ReasonContribution
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Weekly meal plan: recipes assigned to days, plus per-day skip flags. Syncs. */
@Singleton
class MealPlanRepository @Inject constructor(
    private val mealPlanDao: MealPlanDao,
    private val mealDayDao: MealDayDao,
    private val messageRecorder: MessageRecorder,
) {
    private val json = Json
    private val reasonsListSerializer = ListSerializer(ReasonContribution.serializer())

    /** Encode/decode the score breakdown as JSON. Failures (older format, corrupt data)
     *  return null/empty so the UI degrades to "no reasons" rather than crashing. */
    fun encodeReasons(reasons: List<ReasonContribution>): String? =
        if (reasons.isEmpty()) null
        else runCatching { json.encodeToString(reasonsListSerializer, reasons) }.getOrNull()

    fun decodeReasons(jsonString: String?): List<ReasonContribution> {
        if (jsonString.isNullOrEmpty()) return emptyList()
        return runCatching { json.decodeFromString(reasonsListSerializer, jsonString) }
            .getOrDefault(emptyList())
    }

    fun observeForDates(dates: List<String>): Flow<List<MealPlanEntity>> = mealPlanDao.observeForDates(dates)
    fun observeSkipped(dates: List<String>): Flow<List<MealDayEntity>> = mealDayDao.observeForDates(dates)
    suspend fun getForDates(dates: List<String>): List<MealPlanEntity> = mealPlanDao.getForDates(dates)
    suspend fun getForDateRange(start: String, end: String): List<MealPlanEntity> =
        mealPlanDao.getForDateRange(start, end)
    suspend fun skippedDates(dates: List<String>): Set<String> = mealDayDao.skippedDates(dates).toSet()

    suspend fun addEntry(date: String, recipeId: String) {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            recipeId = recipeId,
            pinned = false,
            // Manual add → no reasons; the "?" icon stays hidden for this dish.
            reasonsJson = null,
            createdAt = now,
            updatedAt = now,
        )
        mealPlanDao.upsert(entry)
        messageRecorder.record(MealPlanMessageEncoder.encode(entry))
    }

    suspend fun deleteEntry(id: String) {
        mealPlanDao.deleteById(id)
        messageRecorder.record(MealPlanMessageEncoder.tombstone(id))
    }

    /** Add a dish to [date] already marked cooked — used when you cook something off-plan and
     *  record it as today's actual meal. Returns the new entry id (for undo). */
    suspend fun addCookedEntry(date: String, recipeId: String): String {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            recipeId = recipeId,
            pinned = false,
            cookedAt = date,
            reasonsJson = null,
            createdAt = now,
            updatedAt = now,
        )
        mealPlanDao.upsert(entry)
        messageRecorder.record(MealPlanMessageEncoder.encode(entry))
        return entry.id
    }

    suspend fun setPinned(entryId: String, pinned: Boolean) {
        mealPlanDao.setPinned(entryId, pinned, System.currentTimeMillis())
        mealPlanDao.getById(entryId)?.let { messageRecorder.record(MealPlanMessageEncoder.encode(it)) }
    }

    /** Mark/unmark a planned dish as cooked on its own day (the optional 1-tap). */
    suspend fun setCooked(entryId: String, cooked: Boolean) {
        val entry = mealPlanDao.getById(entryId) ?: return
        val updated = entry.copy(
            cookedAt = if (cooked) entry.date else null,
            updatedAt = System.currentTimeMillis(),
        )
        mealPlanDao.upsert(updated)
        messageRecorder.record(MealPlanMessageEncoder.encode(updated))
    }

    /** Roll an un-cooked planned dish forward to [newDate] (self-healing carry-forward). */
    suspend fun moveEntry(entryId: String, newDate: String) {
        val entry = mealPlanDao.getById(entryId) ?: return
        if (entry.date == newDate) return
        val updated = entry.copy(date = newDate, updatedAt = System.currentTimeMillis())
        mealPlanDao.upsert(updated)
        messageRecorder.record(MealPlanMessageEncoder.encode(updated))
    }

    /** Restore plan entries and day flags from a backup — see
     *  [com.food.opencook.repository.ShoppingRepository.importItems]. The caller is
     *  responsible for dropping entries whose recipe no longer exists. */
    suspend fun importEntries(entries: List<MealPlanEntity>, days: List<MealDayEntity>) {
        if (entries.isEmpty() && days.isEmpty()) return
        entries.forEach { mealPlanDao.upsert(it) }
        days.forEach { mealDayDao.upsert(it) }
        messageRecorder.record(
            entries.flatMap { MealPlanMessageEncoder.encode(it) } +
                days.flatMap { MealDayMessageEncoder.encode(it) },
        )
    }

    suspend fun setSkipped(date: String, skipped: Boolean) {
        val now = System.currentTimeMillis()
        val day = MealDayEntity(date, skipped, now, now)
        mealDayDao.upsert(day)
        messageRecorder.record(MealDayMessageEncoder.encode(day))
        // Skipping a day clears its non-pinned meals so the plan reflects the opt-out.
        if (skipped) clearNonPinned(listOf(date))
    }

    /**
     * Replace the whole week with [generated] (date -> recipeId). Pinned entries are
     * left untouched; everything else in the window is cleared and rebuilt. Skipped
     * days are simply absent from [generated]. [reasons] travel with each entry as
     * `reasonsJson` so other devices can also explain "why this dish?".
     */
    suspend fun generateAndSaveWeek(
        generated: Map<String, String>,
        dateKeys: List<String>,
        reasons: Map<String, List<ReasonContribution>> = emptyMap(),
    ) {
        val pinnedDates = mealPlanDao.getForDates(dateKeys).filter { it.pinned }.map { it.date }.toSet()
        clearNonPinned(dateKeys)
        generated.forEach { (date, recipeId) ->
            if (date !in pinnedDates) insertGenerated(date, recipeId, encodeReasons(reasons[date].orEmpty()))
        }
    }

    /** Swap out a single day's (non-pinned) meal — used by "re-roll this day". */
    suspend fun replaceDay(date: String, recipeId: String, reasons: List<ReasonContribution> = emptyList()) {
        clearNonPinned(listOf(date))
        insertGenerated(date, recipeId, encodeReasons(reasons))
    }

    private suspend fun clearNonPinned(dates: List<String>) {
        mealPlanDao.getForDates(dates).filter { !it.pinned }.forEach { e ->
            mealPlanDao.deleteById(e.id)
            messageRecorder.record(MealPlanMessageEncoder.tombstone(e.id))
        }
    }

    private suspend fun insertGenerated(date: String, recipeId: String, reasonsJson: String? = null) {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            recipeId = recipeId,
            pinned = false,
            reasonsJson = reasonsJson,
            createdAt = now,
            updatedAt = now,
        )
        mealPlanDao.upsert(entry)
        messageRecorder.record(MealPlanMessageEncoder.encode(entry))
    }
}
