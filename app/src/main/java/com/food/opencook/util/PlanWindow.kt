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

package com.food.opencook.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The planner's rolling window: one continuous run of days centred on today, instead of the
 * two fixed Mon–Sun pages it used to page between. Nobody plans in calendar weeks — they plan
 * "the next few days" — so every range the planner works on is derived from today.
 *
 * Three ranges, deliberately different:
 *  - [days] is what the screen shows (a week back for context, a week ahead to plan into).
 *  - [actionDays] is what "suggest" and "shopping list" write to — never the past.
 *  - [futureDays] is the context window a single re-roll reasons over; it has to cover every
 *    visible future day, see the note on that function.
 */
object PlanWindow {

    /** How far back the list reaches — a retrospective, not a planning surface. */
    const val DAYS_BACK = 7L

    /** How far ahead the list reaches. */
    const val DAYS_FORWARD = 7L

    /** Length of the window the top-bar actions write to, starting today. */
    const val ACTION_DAYS = 7L

    /** today−7 … today+7, ascending (15 days) — the whole rolling list. */
    fun days(today: LocalDate = LocalDate.now()): List<LocalDate> =
        range(today.minusDays(DAYS_BACK), DAYS_BACK + DAYS_FORWARD + 1)

    /** today … today+6 — what "suggest" and "add to shopping list" operate on. */
    fun actionDays(today: LocalDate = LocalDate.now()): List<LocalDate> =
        range(today, ACTION_DAYS)

    /**
     * today … today+7 — the visible future part of the list.
     *
     * This is the context window for a single-cell re-roll and for filling one day's gaps.
     * Those go through [com.food.opencook.ui.mealplan.MealPlanner.generateWeek] and then read
     * the target date out of the result: a target outside the window would silently yield
     * nothing. So it must cover every day the user can tap, not just [actionDays].
     */
    fun futureDays(today: LocalDate = LocalDate.now()): List<LocalDate> =
        range(today, DAYS_FORWARD + 1)

    /** One calendar week's worth of the window, keyed by its Monday. */
    data class WeekGroup(val monday: LocalDate, val days: List<LocalDate>)

    /** Splits [days] at calendar-week boundaries, order preserved. */
    fun byWeek(days: List<LocalDate>): List<WeekGroup> =
        days.groupBy { WeekDates.mondayOf(it) }
            .map { (monday, group) -> WeekGroup(monday, group) }
            .sortedBy { it.monday }

    /** −1 = last week, 0 = this week, +1 = next week, relative to [today]. */
    fun weekOffsetOf(monday: LocalDate, today: LocalDate = LocalDate.now()): Int =
        ChronoUnit.WEEKS.between(WeekDates.mondayOf(today), monday).toInt()

    private fun range(start: LocalDate, count: Long): List<LocalDate> =
        (0 until count).map { start.plusDays(it) }
}
