package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A per-day flag on the meal plan. Currently only [skipped] (the user opted a day
 * out, so the auto-planner leaves it empty). Keyed by the ISO date so there is one
 * row per day. Syncs like the rest of the plan.
 */
@Entity(tableName = "meal_days")
data class MealDayEntity(
    @PrimaryKey val date: String,
    val skipped: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
