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

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calendar-week math: always Monday–Sunday, with optional week offset.
 * Shared by the meal planner (current vs. next week toggle) and the
 * recipe → meal-plan picker sheet.
 */
object WeekDates {

    /** All seven days Mon–Sun of the week containing [reference], offset by [weekOffset] full weeks. */
    fun weekOf(reference: LocalDate = LocalDate.now(), weekOffset: Int = 0): List<LocalDate> {
        val monday = mondayOf(reference, weekOffset)
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    /** The Monday of the week containing [reference], shifted by [weekOffset] weeks. */
    fun mondayOf(reference: LocalDate = LocalDate.now(), weekOffset: Int = 0): LocalDate =
        reference.with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
}
