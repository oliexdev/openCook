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

package com.food.opencook.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.food.opencook.data.local.entity.MealPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {

    @Query("SELECT * FROM meal_plan WHERE date IN (:dates) ORDER BY date ASC, createdAt ASC")
    fun observeForDates(dates: List<String>): Flow<List<MealPlanEntity>>

    @Query("SELECT * FROM meal_plan WHERE date IN (:dates)")
    suspend fun getForDates(dates: List<String>): List<MealPlanEntity>

    /** Past entries used as planning history (recency penalty). [start]/[end] inclusive ISO dates. */
    @Query("SELECT * FROM meal_plan WHERE date >= :start AND date <= :end")
    suspend fun getForDateRange(start: String, end: String): List<MealPlanEntity>

    /** How many times a recipe is still planned on [from] or later — "is this dish coming up
     *  at all?", which decides whether its ingredients may leave the shopping list. */
    @Query("SELECT COUNT(*) FROM meal_plan WHERE recipeId = :recipeId AND date >= :from")
    suspend fun countForRecipeFrom(recipeId: String, from: String): Int

    /**
     * The household's cooking history from [from] on: every meal that was actually confirmed
     * cooked, whether it was planned or not — cooking a dish that was never on the plan still
     * writes an entry here (see `MealPlanRepository.addCookedEntry`). The table is never
     * pruned, so this reaches back as far as the household does.
     */
    @Query("SELECT * FROM meal_plan WHERE cookedAt IS NOT NULL AND cookedAt >= :from ORDER BY cookedAt DESC")
    fun observeCookedSince(from: String): Flow<List<MealPlanEntity>>

    /** The whole cooking history, newest first — the retrospective lists it back to the very
     *  first meal the household confirmed. */
    @Query("SELECT * FROM meal_plan WHERE cookedAt IS NOT NULL ORDER BY cookedAt DESC")
    fun observeAllCooked(): Flow<List<MealPlanEntity>>

    /** How often this dish has been cooked, ever — distinct from `recipes.lastCookedAt`, which
     *  only remembers the most recent day. */
    @Query("SELECT COUNT(*) FROM meal_plan WHERE recipeId = :recipeId AND cookedAt IS NOT NULL")
    fun observeCookedCount(recipeId: String): Flow<Int>

    /** Has this household ever cooked anything? Decides whether the plan list offers a way
     *  into the retrospective at all — an entry into an empty screen is worse than none. */
    @Query("SELECT COUNT(*) FROM meal_plan WHERE cookedAt IS NOT NULL")
    fun observeCookedTotal(): Flow<Int>

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun getById(id: String): MealPlanEntity?

    /** Live view of one entry — the recipe screen watches the plan row it was opened from. */
    @Query("SELECT * FROM meal_plan WHERE id = :id")
    fun observeById(id: String): Flow<MealPlanEntity?>

    /** Whole plan — backup export. */
    @Query("SELECT * FROM meal_plan")
    suspend fun getAll(): List<MealPlanEntity>

    @Upsert
    suspend fun upsert(entry: MealPlanEntity)

    @Query("UPDATE meal_plan SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("DELETE FROM meal_plan WHERE id = :id")
    suspend fun deleteById(id: String)
}
