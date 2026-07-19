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
import com.food.opencook.data.local.entity.MealSlot
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

    suspend fun getAllEntries(): List<MealPlanEntity> = mealPlanDao.getAll()
    suspend fun getAllDays(): List<MealDayEntity> = mealDayDao.getAll()

    suspend fun addEntry(date: String, recipeId: String, slot: MealSlot = MealSlot.DINNER) {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            slot = slot.key,
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
    suspend fun addCookedEntry(date: String, recipeId: String, slot: MealSlot = MealSlot.DINNER): String {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            slot = slot.key,
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
    suspend fun moveEntry(entryId: String, newDate: String, newSlot: MealSlot? = null) {
        val entry = mealPlanDao.getById(entryId) ?: return
        val updatedSlot = newSlot?.key ?: entry.slot
        if (entry.date == newDate && entry.slot == updatedSlot) return
        val updated = entry.copy(date = newDate, slot = updatedSlot, updatedAt = System.currentTimeMillis())
        mealPlanDao.upsert(updated)
        messageRecorder.record(MealPlanMessageEncoder.encode(updated))
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
        slot: MealSlot = MealSlot.DINNER,
        reasons: Map<String, List<ReasonContribution>> = emptyMap(),
    ) {
        val pinnedDates = mealPlanDao.getForDates(dateKeys)
            .filter { it.pinned && it.slot == slot.key }
            .map { it.date }.toSet()
        clearNonPinned(dateKeys, listOf(slot))
        generated.forEach { (date, recipeId) ->
            if (date !in pinnedDates) insertGenerated(date, recipeId, slot, encodeReasons(reasons[date].orEmpty()))
        }
    }

    /** Swap out a single day's (non-pinned) meal — used by "re-roll this day". */
    suspend fun replaceDay(date: String, recipeId: String, slot: MealSlot = MealSlot.DINNER, reasons: List<ReasonContribution> = emptyList()) {
        clearNonPinned(listOf(date), listOf(slot))
        insertGenerated(date, recipeId, slot, encodeReasons(reasons))
    }

    private suspend fun clearNonPinned(dates: List<String>, slots: List<MealSlot>? = null) {
        mealPlanDao.getForDates(dates)
            .filter { !it.pinned && (slots == null || MealSlot.fromKey(it.slot) in slots) }
            .forEach { e ->
                mealPlanDao.deleteById(e.id)
                messageRecorder.record(MealPlanMessageEncoder.tombstone(e.id))
            }
    }

    private suspend fun insertGenerated(date: String, recipeId: String, slot: MealSlot, reasonsJson: String? = null) {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            slot = slot.key,
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
