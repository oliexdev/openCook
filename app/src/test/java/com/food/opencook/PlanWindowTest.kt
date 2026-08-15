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

package com.food.opencook

import com.food.opencook.ui.mealplan.DayPlan
import com.food.opencook.ui.mealplan.SlotPlan
import com.food.opencook.ui.mealplan.sectionsOf
import com.food.opencook.util.PlanWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class PlanWindowTest {

    /** A Wednesday, so week boundaries fall inside the window rather than at its edges. */
    private val wednesday: LocalDate = LocalDate.of(2026, 8, 12)

    @Test
    fun `window spans a week either side of today`() {
        val days = PlanWindow.days(wednesday)
        assertEquals(15, days.size)
        assertEquals(LocalDate.of(2026, 8, 5), days.first())
        assertEquals(wednesday, days[7])
        assertEquals(LocalDate.of(2026, 8, 19), days.last())
        assertEquals(days.sorted(), days)
    }

    @Test
    fun `action window is today plus six, never the past`() {
        val days = PlanWindow.actionDays(wednesday)
        assertEquals(7, days.size)
        assertEquals(wednesday, days.first())
        assertEquals(LocalDate.of(2026, 8, 18), days.last())
        assertTrue(days.none { it < wednesday })
    }

    /**
     * The re-roll path hands these dates to the planner and then reads the target back out of
     * the result — so every future day the list renders has to be in here, or tapping the
     * wand on the last day would silently do nothing.
     */
    @Test
    fun `reroll context covers every visible future day`() {
        val visibleFuture = PlanWindow.days(wednesday).filter { it >= wednesday }
        val context = PlanWindow.futureDays(wednesday)
        assertEquals(visibleFuture, context)
    }

    @Test
    fun `window splits into calendar weeks across a month boundary`() {
        // A Sunday: the window runs Sun 23 Aug … Sun 6 Sep, so the first week contributes a
        // single day and the split has to survive the month change.
        val groups = PlanWindow.byWeek(PlanWindow.days(LocalDate.of(2026, 8, 30)))
        assertEquals(listOf(1, 7, 7), groups.map { it.days.size })
        assertEquals(
            listOf(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 31)),
            groups.map { it.monday },
        )
        // Nothing lost, nothing reordered, and every day sits inside its own week.
        assertEquals(PlanWindow.days(LocalDate.of(2026, 8, 30)), groups.flatMap { it.days })
        groups.forEach { group ->
            assertTrue(group.days.all { it >= group.monday && it <= group.monday.plusDays(6) })
        }
    }

    @Test
    fun `week offsets are relative to today`() {
        val monday = LocalDate.of(2026, 8, 10) // the Monday of the Wednesday above
        assertEquals(0, PlanWindow.weekOffsetOf(monday, wednesday))
        assertEquals(-1, PlanWindow.weekOffsetOf(monday.minusWeeks(1), wednesday))
        assertEquals(1, PlanWindow.weekOffsetOf(monday.plusWeeks(1), wednesday))
        // Sunday still belongs to the week that started on the Monday before it.
        val sunday = LocalDate.of(2026, 8, 16)
        assertEquals(0, PlanWindow.weekOffsetOf(monday, sunday))
        assertEquals(1, PlanWindow.weekOffsetOf(monday.plusWeeks(1), sunday))
    }

    // --- sectionsOf -------------------------------------------------------------------

    private fun day(date: LocalDate, planned: Boolean = true) = DayPlan(
        date = date.toString(),
        label = date.toString(),
        slots = if (planned) listOf(SlotPlan("lunch", emptyList(), hasCandidates = true)) else emptyList(),
    )

    @Test
    fun `today index counts the sticky headers`() {
        val week = PlanWindow.days(wednesday).map { day(it) }
        val (sections, index) = sectionsOf(week, wednesday.toString())
        assertEquals(listOf(-1, 0, 1), sections.map { it.weekOffset })
        // last week: header + Wed–Sun (5 days) = 6 items; then this week's header at 6,
        // Mon at 7, Tue at 8, Wed (today) at 9.
        assertEquals(5, sections.first().days.size)
        assertEquals(9, index)
    }

    /** Defensive: the index must not assume a full window, so a list that happens to start at
     *  today still resolves — the header before it counts, nothing before that does. */
    @Test
    fun `today index holds when the list starts at today`() {
        val week = PlanWindow.days(wednesday).filter { it >= wednesday }.map { day(it) }
        val (sections, index) = sectionsOf(week, wednesday.toString())
        assertEquals(listOf(0, 1), sections.map { it.weekOffset })
        assertEquals(1, index) // header, then today
    }

    @Test
    fun `no today in the list yields no jump target`() {
        val week = PlanWindow.days(wednesday).filter { it > wednesday }.map { day(it) }
        assertEquals(-1, sectionsOf(week, wednesday.toString()).second)
        assertEquals(-1, sectionsOf(emptyList(), wednesday.toString()).second)
    }
}
