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
import com.food.opencook.ui.mealplan.MealPlanSlots
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
    fun observeEntry(id: String): Flow<MealPlanEntity?> = mealPlanDao.observeById(id)
    suspend fun getForEntry(id: String): MealPlanEntity? = mealPlanDao.getById(id)
    fun observeSkipped(dates: List<String>): Flow<List<MealDayEntity>> = mealDayDao.observeForDates(dates)
    suspend fun getForDates(dates: List<String>): List<MealPlanEntity> = mealPlanDao.getForDates(dates)
    suspend fun getForDateRange(start: String, end: String): List<MealPlanEntity> =
        mealPlanDao.getForDateRange(start, end)
    suspend fun skippedDates(dates: List<String>): Set<String> = mealDayDao.skippedDates(dates).toSet()

    suspend fun addEntry(date: String, recipeId: String, slot: String) {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            recipeId = recipeId,
            slot = slot,
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
    suspend fun addCookedEntry(date: String, recipeId: String, slot: String): String {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            recipeId = recipeId,
            slot = slot,
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

    /** Move an un-cooked planned dish to another cell — the drag-and-drop target and the
     *  self-healing carry-forward. [newSlot] null keeps the dish in its current meal. */
    suspend fun moveEntry(entryId: String, newDate: String, newSlot: String? = null) {
        val entry = mealPlanDao.getById(entryId) ?: return
        val slot = newSlot ?: entry.slot
        if (entry.date == newDate && entry.slot == slot) return
        val updated = entry.copy(date = newDate, slot = slot, updatedAt = System.currentTimeMillis())
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
        if (skipped) clearNonPinned(mealPlanDao.getForDates(listOf(date)))
    }

    /**
     * Replace one meal of the week with [generated] (date -> recipeId). Pinned entries are
     * left untouched; every other entry of *this slot* in the window is cleared and rebuilt —
     * so regenerating lunches never touches a hand-placed breakfast or a one-off Sunday cake.
     * [reasons] travel with each entry as `reasonsJson` so other devices can also explain
     * "why this dish?". [planned] is needed to place entries that predate slots (null slot).
     */
    suspend fun generateAndSaveWeek(
        slot: String,
        generated: Map<String, String>,
        dateKeys: List<String>,
        planned: List<String>,
        reasons: Map<String, List<ReasonContribution>> = emptyMap(),
    ) {
        val inSlot = mealPlanDao.getForDates(dateKeys).filter { MealPlanSlots.resolve(it.slot, planned) == slot }
        val pinnedDates = inSlot.filter { it.pinned }.map { it.date }.toSet()
        clearNonPinned(inSlot)
        generated.forEach { (date, recipeId) ->
            if (date !in pinnedDates) insertGenerated(date, slot, recipeId, encodeReasons(reasons[date].orEmpty()))
        }
    }

    /** Swap out a single (non-pinned) cell — used by "re-roll this meal". */
    suspend fun replaceCell(
        date: String,
        slot: String,
        recipeId: String,
        planned: List<String>,
        reasons: List<ReasonContribution> = emptyList(),
    ) {
        val inCell = mealPlanDao.getForDates(listOf(date)).filter { MealPlanSlots.resolve(it.slot, planned) == slot }
        clearNonPinned(inCell)
        insertGenerated(date, slot, recipeId, encodeReasons(reasons))
    }

    private suspend fun clearNonPinned(entries: List<MealPlanEntity>) {
        entries.filter { !it.pinned }.forEach { e ->
            mealPlanDao.deleteById(e.id)
            messageRecorder.record(MealPlanMessageEncoder.tombstone(e.id))
        }
    }

    private suspend fun insertGenerated(
        date: String,
        slot: String,
        recipeId: String,
        reasonsJson: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            recipeId = recipeId,
            slot = slot,
            pinned = false,
            reasonsJson = reasonsJson,
            createdAt = now,
            updatedAt = now,
        )
        mealPlanDao.upsert(entry)
        messageRecorder.record(MealPlanMessageEncoder.encode(entry))
    }
}
