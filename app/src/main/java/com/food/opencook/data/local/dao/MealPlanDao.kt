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

    @Upsert
    suspend fun upsert(entry: MealPlanEntity)

    @Query("UPDATE meal_plan SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("DELETE FROM meal_plan WHERE id = :id")
    suspend fun deleteById(id: String)
}
