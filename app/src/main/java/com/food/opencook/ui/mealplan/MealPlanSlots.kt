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

package com.food.opencook.ui.mealplan

import androidx.annotation.StringRes
import com.food.opencook.R
import com.food.opencook.util.MealTypes

/**
 * Which meals of the day the planner works with.
 *
 * Two different things share the [MealTypes.KEYS] vocabulary:
 * - `recipes.mealTypes` says *what a recipe is suitable for* (a capability, multi-value).
 * - `meal_plan.slot` says *when a planned entry happens* (a position, single-value).
 *
 * This object owns the second one, plus the household's "which meals do we plan at all?"
 * setting. It is deliberately pure Kotlin (no Android deps beyond string ids) so the
 * resolution rules are unit-testable.
 */
object MealPlanSlots {

    /** What the planner was before slots existed: a single dish per day, i.e. lunch. */
    const val PRIMARY = "lunch"

    /** Household default when the setting was never touched — reproduces the old behaviour. */
    val DEFAULT_PLANNED = listOf(PRIMARY)

    /** Sort any set of slot keys into the order of the day. */
    fun order(keys: Collection<String>): List<String> = MealTypes.KEYS.filter { it in keys }

    /** Stored setting column → active slots, never empty (falls back to [DEFAULT_PLANNED]). */
    fun plannedFromStored(stored: String?): List<String> {
        val keys = stored?.split("\n")?.mapNotNull { MealTypes.normalizeKey(it) }?.distinct().orEmpty()
        return order(keys).ifEmpty { DEFAULT_PLANNED }
    }

    /** Slot list → stored column. Empty → null, so the defaults apply again. */
    fun toStored(keys: List<String>): String? = order(keys).takeIf { it.isNotEmpty() }?.joinToString("\n")

    /**
     * Where a stored entry belongs. Entries written before slots existed have `null` and
     * land on [PRIMARY]; if the household does not plan lunch at all, they go to the first
     * active slot instead, so an old entry can never become invisible.
     */
    fun resolve(stored: String?, planned: List<String>): String {
        MealTypes.normalizeKey(stored)?.let { return it }
        if (PRIMARY in planned) return PRIMARY
        return planned.firstOrNull() ?: PRIMARY
    }

    /**
     * Rows to render for one day: first every planned slot in order of the day, then any
     * slot that is *not* planned but has an entry (a deliberate one-off, e.g. Sunday cake
     * while "snack" is globally off).
     *
     * The one-offs stay at the bottom rather than sorting into the day, and they stay there
     * once filled: the household's selection defines the shape of a normal day, so a meal it
     * doesn't contain must not push the planned rows around. Enable it in the settings and
     * it moves up into its chronological place.
     */
    fun rowsFor(planned: List<String>, occupied: Collection<String>): List<String> =
        order(planned) + order(occupied.toSet() - planned.toSet())

    /**
     * The meal that "now" belongs to — what the home screen leads with and what "I cooked
     * this" assumes you just cooked. The cut-offs are the ordinary shape of a day rather
     * than exact meal times: nobody plans breakfast at 3pm, and being an hour off only
     * changes which dish is offered first, never what is stored.
     *
     * If that meal isn't planned, the next one later in the day wins (10am with only dinner
     * planned → dinner); if the day is already past its last planned meal, the last one wins.
     */
    fun currentSlot(planned: List<String>, hour: Int): String {
        val preferred = when {
            hour < 10 -> "breakfast"
            hour < 14 -> "lunch"
            hour < 17 -> "snack"
            else -> "dinner"
        }
        if (preferred in planned) return preferred
        val from = MealTypes.KEYS.indexOf(preferred)
        return MealTypes.KEYS.drop(from).firstOrNull { it in planned }
            ?: MealTypes.KEYS.take(from).lastOrNull { it in planned }
            ?: PRIMARY
    }

    /** Short label for the slot column ("Früh"), distinct from the long recipe-editor label
     *  ("Snack / Kaffee & Kuchen") which is far too wide for a 44dp column. */
    @StringRes
    fun shortLabelRes(slot: String): Int = when (slot) {
        "breakfast" -> R.string.mealtype_short_breakfast
        "lunch" -> R.string.mealtype_short_lunch
        "snack" -> R.string.mealtype_short_snack
        else -> R.string.mealtype_short_dinner
    }
}
