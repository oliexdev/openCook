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

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun getById(id: String): MealPlanEntity?

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
