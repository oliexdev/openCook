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

import com.food.opencook.data.local.dao.MealDayDao
import com.food.opencook.data.local.dao.MealPlanDao
import com.food.opencook.data.local.entity.MealDayEntity
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.sync.MessageRecorder
import com.food.opencook.sync.SyncDatasets
import com.food.opencook.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory meal plan. [knownRecipeIds] stands in for the recipes table that
 *  `orphanEntryIds` joins against. */
internal class FakeMealPlanDao : MealPlanDao {
    val entries = linkedMapOf<String, MealPlanEntity>()
    val knownRecipeIds = mutableSetOf<String>()

    override fun observeForDates(dates: List<String>): Flow<List<MealPlanEntity>> = throw NotImplementedError()
    override suspend fun getForDates(dates: List<String>): List<MealPlanEntity> =
        entries.values.filter { it.date in dates }
    override suspend fun getForDateRange(start: String, end: String): List<MealPlanEntity> =
        entries.values.filter { it.date in start..end }
    override suspend fun countForRecipeFrom(recipeId: String, from: String): Int =
        entries.values.count { it.recipeId == recipeId && it.date >= from }
    override fun observeCookedSince(from: String): Flow<List<MealPlanEntity>> = throw NotImplementedError()
    override fun observeAllCooked(): Flow<List<MealPlanEntity>> = throw NotImplementedError()
    override fun observeCookedCount(recipeId: String): Flow<Int> = throw NotImplementedError()
    override fun observeCookedTotal(): Flow<Int> = throw NotImplementedError()
    override suspend fun getById(id: String): MealPlanEntity? = entries[id]
    override fun observeById(id: String): Flow<MealPlanEntity?> = throw NotImplementedError()
    override suspend fun getAll(): List<MealPlanEntity> = entries.values.toList()
    override suspend fun upsert(entry: MealPlanEntity) { entries[entry.id] = entry }
    override suspend fun setPinned(id: String, pinned: Boolean, now: Long) {
        entries[id]?.let { entries[id] = it.copy(pinned = pinned, updatedAt = now) }
    }
    override suspend fun deleteById(id: String) { entries.remove(id) }
    override suspend fun idsForRecipe(recipeId: String): List<String> =
        entries.values.filter { it.recipeId == recipeId }.map { it.id }
    override suspend fun orphanEntryIds(): List<String> =
        entries.values.filter { it.recipeId !in knownRecipeIds }.map { it.id }
}

internal class FakeMealDayDao : MealDayDao {
    val days = linkedMapOf<String, MealDayEntity>()
    override fun observeForDates(dates: List<String>): Flow<List<MealDayEntity>> = throw NotImplementedError()
    override suspend fun skippedDates(dates: List<String>): List<String> =
        days.values.filter { it.skipped && it.date in dates }.map { it.date }
    override suspend fun autoPlannedDates(dates: List<String>): List<String> =
        days.values.filter { it.autoPlanned && it.date in dates }.map { it.date }
    override suspend fun getByDate(date: String): MealDayEntity? = days[date]
    override suspend fun getAll(): List<MealDayEntity> = days.values.toList()
    override suspend fun upsert(day: MealDayEntity) { days[day.date] = day }
    override suspend fun deleteByDate(date: String) { days.remove(date) }
}

internal fun planEntry(id: String, recipeId: String, date: String, cookedAt: String? = null) =
    MealPlanEntity(
        id = id,
        date = date,
        recipeId = recipeId,
        slot = "dinner",
        cookedAt = cookedAt,
        createdAt = 0,
        updatedAt = 0,
    )

/**
 * A plan entry whose recipe is gone can't be drawn — no name, no photo, and the recipe
 * screen it opens has nothing to load. These are the two ways it gets cleaned up.
 */
class MealPlanCleanupTest {

    private fun fixture(): Triple<FakeMealPlanDao, FakeMessageDao, MealPlanRepository> {
        val planDao = FakeMealPlanDao()
        val messageDao = FakeMessageDao()
        val recorder = MessageRecorder(
            messageDao,
            FakeStamper(),
            object : SyncTrigger { override fun requestSync() {} },
        )
        return Triple(planDao, messageDao, MealPlanRepository(planDao, FakeMealDayDao(), recorder))
    }

    @Test
    fun deletingARecipeTakesItsPlanEntriesAndTombstonesThem() = runTest {
        val (planDao, messageDao, repo) = fixture()
        planDao.entries["e1"] = planEntry("e1", "r1", "2026-09-14")
        planDao.entries["e2"] = planEntry("e2", "r1", "2026-09-20", cookedAt = "2026-09-20")
        planDao.entries["e3"] = planEntry("e3", "r2", "2026-09-15")

        repo.deleteEntriesForRecipe("r1")

        assertEquals(listOf("e3"), planDao.entries.keys.toList())
        // Both removals travel, so the household's other devices lose them too.
        val tombstoned = messageDao.messages
            .filter { it.dataset == SyncDatasets.MEALPLAN && it.column == SyncDatasets.COLUMN_DELETED }
            .map { it.rowId }
            .toSet()
        assertEquals(setOf("e1", "e2"), tombstoned)
    }

    @Test
    fun purgeDropsEntriesWhoseRecipeIsGone() = runTest {
        val (planDao, messageDao, repo) = fixture()
        planDao.knownRecipeIds += "r2"
        planDao.entries["e1"] = planEntry("e1", "r1", "2026-09-14") // orphan
        planDao.entries["e3"] = planEntry("e3", "r2", "2026-09-15")

        assertEquals(1, repo.purgeOrphanEntries())

        assertEquals(listOf("e3"), planDao.entries.keys.toList())
        // Local-only: "the recipe isn't here" is this device's view, and a peer that still
        // has it must keep its entry.
        assertTrue(messageDao.messages.none { it.dataset == SyncDatasets.MEALPLAN })
    }
}
