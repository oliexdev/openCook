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

    /** Is this dish still on the plan for [from] or later? Days gone by don't count — nobody
     *  shops for a meal that has already passed. */
    suspend fun isPlannedFrom(recipeId: String, from: String): Boolean =
        mealPlanDao.countForRecipeFrom(recipeId, from) > 0

    /** Confirmed-cooked entries from [from] on — feeds the plan list's retrospective teaser. */
    fun observeCookedSince(from: String): Flow<List<MealPlanEntity>> = mealPlanDao.observeCookedSince(from)

    /** The household's complete cooking history, newest first. */
    fun observeAllCooked(): Flow<List<MealPlanEntity>> = mealPlanDao.observeAllCooked()

    /** How often this dish has been cooked, ever. */
    fun observeCookedCount(recipeId: String): Flow<Int> = mealPlanDao.observeCookedCount(recipeId)

    /** Total confirmed-cooked meals — "does this household have a history yet?". */
    fun observeCookedTotal(): Flow<Int> = mealPlanDao.observeCookedTotal()

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
        writeDay(date) { it.copy(skipped = skipped) }
        // Skipping a day clears its non-pinned meals so the plan reflects the opt-out.
        if (skipped) clearNonPinned(mealPlanDao.getForDates(listOf(date)))
    }

    /** Days the rolling planner has already offered itself for; it never returns to them. */
    suspend fun autoPlannedDates(dates: List<String>): Set<String> =
        mealDayDao.autoPlannedDates(dates).toSet()

    /**
     * Remember that the planner has handled [date] — whether or not a dish came out of it.
     * That "whether or not" is the point: a day it declined (nothing outside the repeat
     * cool-down) must not be retried tomorrow, or a small library would get a fresh attempt
     * every single day until something slipped through.
     */
    suspend fun markAutoPlanned(date: String) {
        writeDay(date) { it.copy(autoPlanned = true) }
    }

    /** Read-modify-write one day row, so setting one flag never clears the other. */
    private suspend fun writeDay(date: String, edit: (MealDayEntity) -> MealDayEntity) {
        val now = System.currentTimeMillis()
        val existing = mealDayDao.getByDate(date)
            ?: MealDayEntity(date = date, skipped = false, createdAt = now, updatedAt = now)
        val day = edit(existing).copy(updatedAt = now)
        mealDayDao.upsert(day)
        messageRecorder.record(MealDayMessageEncoder.encode(day))
    }

    /**
     * Put the planner's pick into one cell — **only if that cell is empty**. The rolling
     * planner never overwrites: it offers itself for a day once, when the day enters the
     * window, and whatever is there afterwards is the household's business.
     *
     * The entry id is derived from `(date, slot)` instead of being random, so two devices
     * that fill the same cell while out of contact write the *same* row and per-field
     * last-write-wins settles on one dish, rather than the day ending up with two dinners.
     */
    suspend fun autoFillCell(
        date: String,
        slot: String,
        recipeId: String,
        planned: List<String>,
        reasons: List<ReasonContribution> = emptyList(),
    ) {
        val occupied = mealPlanDao.getForDates(listOf(date))
            .any { MealPlanSlots.resolve(it.slot, planned) == slot }
        if (occupied) return
        insertGenerated(date, slot, recipeId, encodeReasons(reasons), deterministicId = true)
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
        /** Auto-filled cells derive their id so concurrent devices converge (see [autoFillCell]).
         *  Hand-placed ones keep a random id — there a second dish on the same day is a
         *  legitimate thing to want. */
        deterministicId: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val entry = MealPlanEntity(
            id = if (deterministicId) autoEntryId(date, slot) else UUID.randomUUID().toString(),
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

    companion object {
        /** Stable id for an auto-filled cell: same day + same meal ⇒ same row on every device,
         *  so two phones filling the window offline converge instead of doubling the dish. */
        internal fun autoEntryId(date: String, slot: String): String =
            UUID.nameUUIDFromBytes("auto|$date|$slot".toByteArray()).toString()
    }
}
