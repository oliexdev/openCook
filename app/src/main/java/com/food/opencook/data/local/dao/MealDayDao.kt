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
import com.food.opencook.data.local.entity.MealDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDayDao {

    @Query("SELECT * FROM meal_days WHERE date IN (:dates)")
    fun observeForDates(dates: List<String>): Flow<List<MealDayEntity>>

    @Query("SELECT date FROM meal_days WHERE skipped = 1 AND date IN (:dates)")
    suspend fun skippedDates(dates: List<String>): List<String>

    @Query("SELECT * FROM meal_days WHERE date = :date")
    suspend fun getByDate(date: String): MealDayEntity?

    @Upsert
    suspend fun upsert(day: MealDayEntity)

    @Query("DELETE FROM meal_days WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
