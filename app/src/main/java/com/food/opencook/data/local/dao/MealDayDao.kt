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
