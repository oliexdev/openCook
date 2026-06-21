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
