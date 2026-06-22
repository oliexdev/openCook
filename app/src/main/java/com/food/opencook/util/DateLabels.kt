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

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Locale-aware date labels for the planner/recipe screens. Instead of a fixed
 * German-style "EEE dd.MM." pattern, derive the day+month order from the device
 * locale (no year — the planner only spans the current week). E.g. en → "Mon, 6/22",
 * de → "Mo., 22.6.".
 */
object DateLabels {
    fun weekdayDayMonth(fullWeekday: Boolean = false): DateTimeFormatter {
        val locale = Locale.getDefault()
        val skeleton = if (fullWeekday) "EEEEMd" else "EEEMd"
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}
