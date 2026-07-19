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
import kotlinx.serialization.Serializable

/** The three fixed meal slots of a day. */
enum class MealSlot(val key: String) {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner"),
    ;
    companion object {
        fun fromKey(key: String?) = entries.find { it.key == key } ?: DINNER
    }
}

/** A recipe planned for a given day and slot. [date] is ISO "yyyy-MM-dd". Syncs like recipes. */
@Entity(tableName = "meal_plan")
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val date: String,
    val slot: String = MealSlot.DINNER.key,
    val recipeId: String,
    /** Pinned entries survive auto-regeneration ("Woche vorschlagen" / re-roll). */
    val pinned: Boolean = false,
    /** Score breakdown that produced this pick, as a JSON-encoded
     *  `List<MealPlanner.ReasonContribution>`. Null for manually-added entries (the
     *  user picked the recipe themselves, no algorithmic reason). Syncs along with
     *  the plan so other household devices can also explain "why this dish?". */
    val reasonsJson: String? = null,
    /** ISO "yyyy-MM-dd" of the day this dish was confirmed cooked, or null if not
     *  confirmed. Per-entry (distinct from recipes.lastCookedAt, which is per-recipe):
     *  the same recipe can be planned on several days, and the self-healing roll-forward
     *  needs to know whether *this* day's meal actually happened. Syncs with the plan. */
    val cookedAt: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
