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

import com.food.opencook.util.PlanWindow
import com.food.opencook.util.WeekDates
import java.time.LocalDate

/** One sticky-header group of the rolling list. [weekOffset] is −1/0/+1 relative to today. */
data class PlanSection(val weekOffset: Int, val days: List<DayPlan>)

/**
 * The rolling list, cut into calendar weeks, plus the LazyColumn index of today.
 *
 * The index has to be computed here rather than in the screen because headers are list items
 * too — the auto-scroll on open and the "today" button both need the same number, and
 * recomputing it inline in the composable is exactly how the two drift apart.
 *
 * Returns −1 as the index when today is not in [week] (an empty list before the first
 * emission, or a past day that dropped out because nothing was planned on it).
 */
fun sectionsOf(week: List<DayPlan>, today: String): Pair<List<PlanSection>, Int> {
    if (week.isEmpty()) return emptyList<PlanSection>() to -1
    val todayDate = runCatching { LocalDate.parse(today) }.getOrNull()
    val sections = week
        .groupBy { WeekDates.mondayOf(LocalDate.parse(it.date)) }
        .toSortedMap()
        .map { (monday, days) ->
            PlanSection(
                weekOffset = todayDate?.let { PlanWindow.weekOffsetOf(monday, it) } ?: 0,
                days = days,
            )
        }

    var index = 0
    sections.forEach { section ->
        index++ // the section's sticky header
        val inSection = section.days.indexOfFirst { it.date == today }
        if (inSection >= 0) return sections to index + inSection
        index += section.days.size
    }
    return sections to -1
}
