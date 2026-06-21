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

import java.time.LocalDate

/**
 * Pure self-healing logic for the meal plan — no Android/Room/IO, so it's directly
 * unit-testable.
 *
 * Real life diverges from the plan: a dish planned for Monday often isn't cooked that
 * day (you ate out, improvised, cooked something not in the app). The app never asks
 * "what did you eat?" — for the planner those cases are identical: the planned dish
 * didn't happen. So the rule is "doing nothing must always be correct".
 *
 * When a planned day has passed without a "cooked" confirmation, this carries the dish
 * forward **only if its ingredients were procured** (bought, or already in the pantry) —
 * otherwise the food isn't on hand and moving it would be pointless. A procured dish
 * takes the next free, non-skipped day within [DEFAULT_WINDOW_DAYS]; if none is free it
 * simply stays put (rendered faded in the UI). Nothing is ever deleted — past entries
 * remain as planning history that feeds the recency penalty.
 *
 * "Next free day" (not a hard shift of the whole remaining week) is deliberate: it's
 * predictable and mirrors the leftover placement in [MealPlanner].
 */
object MealPlanReconciler {

    /** Carry [entryId] forward to [toDate]. Only procured, un-cooked past entries get one. */
    data class Move(val entryId: String, val toDate: LocalDate)

    /** A past, un-cooked, non-pinned planned entry that's a candidate to roll forward. */
    data class PastEntry(val entryId: String, val date: LocalDate, val procured: Boolean)

    const val DEFAULT_WINDOW_DAYS = 2L

    /**
     * @param pastUncooked entries with date < [today], cookedAt == null, not pinned.
     * @param occupied days that already hold a plan entry (today/future) — never overwritten.
     * @param skipped days the user opted out of.
     * @param window carry at most this many days past [today].
     */
    fun reconcile(
        pastUncooked: List<PastEntry>,
        occupied: Set<LocalDate>,
        skipped: Set<LocalDate>,
        today: LocalDate,
        window: Long = DEFAULT_WINDOW_DAYS,
    ): List<Move> {
        val taken = occupied.toMutableSet()
        val moves = mutableListOf<Move>()
        // Oldest first so the longest-waiting dish claims the earliest free slot.
        pastUncooked.asSequence()
            .filter { it.procured }
            .sortedBy { it.date }
            .forEach { entry ->
                val slot = (0..window)
                    .map { today.plusDays(it) }
                    .firstOrNull { it !in taken && it !in skipped }
                    ?: return@forEach // no nearby free day → leave it where it is
                taken += slot
                moves += Move(entry.entryId, slot)
            }
        return moves
    }
}
