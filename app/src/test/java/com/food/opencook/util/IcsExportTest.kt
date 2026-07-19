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

import com.food.opencook.ui.mealplan.DayPlan
import com.food.opencook.ui.mealplan.PlannedRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExportTest {

    private fun dish(name: String) = PlannedRecipe(entryId = "e-$name", recipeId = "r-$name", name = name, pinned = false)

    @Test
    fun `empty week produces a calendar with no events`() {
        val week = listOf(DayPlan(date = "2026-07-20", label = "Mon", entries = emptyList()))

        val ics = IcsExport.buildWeekIcs(week)

        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    @Test
    fun `a planned day becomes an all-day event spanning just that day`() {
        val week = listOf(DayPlan(date = "2026-07-20", label = "Mon", entries = listOf(dish("Pasta"))))

        val ics = IcsExport.buildWeekIcs(week)

        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260720"))
        // All-day events use an exclusive end date in iCal — the day after.
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260721"))
        assertTrue(ics.contains("SUMMARY:Pasta"))
    }

    @Test
    fun `multiple dishes on one day join into a single summary`() {
        val week = listOf(DayPlan(date = "2026-07-20", label = "Mon", entries = listOf(dish("Pasta"), dish("Salad"))))

        val ics = IcsExport.buildWeekIcs(week)

        assertTrue(ics.contains("SUMMARY:Pasta + Salad"))
    }

    @Test
    fun `same date always produces the same UID so re-exports are recognisable as updates`() {
        val week = listOf(DayPlan(date = "2026-07-20", label = "Mon", entries = listOf(dish("Pasta"))))

        val first = IcsExport.buildWeekIcs(week)
        val second = IcsExport.buildWeekIcs(listOf(DayPlan(date = "2026-07-20", label = "Mon", entries = listOf(dish("Soup")))))

        val uidOf = { text: String -> text.lines().first { it.startsWith("UID:") } }
        assertEquals(uidOf(first), uidOf(second))
    }

    @Test
    fun `special characters in dish names are escaped per RFC 5545`() {
        val week = listOf(DayPlan(date = "2026-07-20", label = "Mon", entries = listOf(dish("Rice, Beans; Cheese"))))

        val ics = IcsExport.buildWeekIcs(week)

        assertTrue(ics.contains("SUMMARY:Rice\\, Beans\\; Cheese"))
    }

    @Test
    fun `lines are joined with CRLF as RFC 5545 requires`() {
        val week = listOf(DayPlan(date = "2026-07-20", label = "Mon", entries = listOf(dish("Pasta"))))

        val ics = IcsExport.buildWeekIcs(week)

        assertTrue(ics.contains("\r\n"))
        assertFalse(ics.replace("\r\n", "").contains("\n"))
    }
}
