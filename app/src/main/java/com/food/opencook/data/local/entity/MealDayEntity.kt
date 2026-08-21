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
 * Per-day facts about the meal plan — not about a single meal. Keyed by the ISO date, so
 * there is exactly one row per day and, crucially, **the sync row id is the date itself**:
 * two devices recording the same day write the same row, and last-write-wins merges them
 * instead of producing a duplicate.
 *
 * Syncs like the rest of the plan.
 */
@Entity(tableName = "meal_days")
data class MealDayEntity(
    @PrimaryKey val date: String,
    /** Left over from the removed "skip a day" affordance; always false since June 2026. */
    val skipped: Boolean,
    /**
     * The rolling planner has already had this day in its hands — whether or not a dish came
     * out of it. This is what makes auto-filling a *edge* operation instead of a sweep: a day
     * is offered to the planner exactly once, when it rolls into the forward window, and is
     * never revisited. That is what protects every manual change, including the one that is
     * otherwise impossible to detect — deleting a dish.
     */
    val autoPlanned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
