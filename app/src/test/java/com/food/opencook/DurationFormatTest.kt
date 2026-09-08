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

import com.food.opencook.util.DurationFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DurationFormatTest {

    // The unit words and labels come from arrays.xml/strings.xml at runtime; a JVM test seeds
    // them itself. Parsing always accepts every language at once — that is the point of the
    // union — while the label is whatever the phone's language writes.
    private val hourWords = listOf("stunden", "stunde", "std", "h", "heures", "heure", "hours", "hour")
    private val minuteWords = listOf("minuten", "minute", "min", "m", "minutes", "mn", "mins")

    @Before fun german() {
        DurationFormat.setUnits(hourWords, minuteWords)
        // On a device this is android.icu MeasureFormat in the device locale; here a stand-in,
        // so the assertions stay about *our* logic and not about ICU's wording.
        DurationFormat.setRenderer { h, m -> render(h, m, "Std", "Min") }
    }

    @After fun restore() = DurationFormat.setRenderer { h, m -> render(h, m, "h", "min") }

    private fun render(h: Int, m: Int, hLabel: String, mLabel: String) = listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it $hLabel" },
        m.takeIf { it > 0 }?.let { "$it $mLabel" },
    ).joinToString(" ")

    @Test
    fun isoToHuman() {
        assertEquals("25 Min", DurationFormat.toHuman("PT25M"))
        assertEquals("1 Std", DurationFormat.toHuman("PT1H"))
        assertEquals("1 Std 10 Min", DurationFormat.toHuman("PT1H10M"))
        assertEquals("", DurationFormat.toHuman(null))
        assertEquals("", DurationFormat.toHuman(""))
    }

    @Test
    fun isoToHumanEnglishLocale() {
        DurationFormat.setRenderer { h, m -> render(h, m, "h", "min") }
        assertEquals("25 min", DurationFormat.toHuman("PT25M"))
        assertEquals("1 h", DurationFormat.toHuman("PT1H"))
        assertEquals("1 h 10 min", DurationFormat.toHuman("PT1H10M"))
        // English units must still round-trip back to ISO.
        assertEquals("PT70M", DurationFormat.toIso(DurationFormat.toHuman("PT1H10M")))
    }

    @Test
    fun nonIsoPassesThrough() {
        assertEquals("über Nacht", DurationFormat.toHuman("über Nacht"))
    }

    // GitHub issue #2: second-based and zero durations (e.g. from Japanese recipes) must
    // not leak raw ISO to the UI.
    @Test
    fun secondBasedAndZeroDurations() {
        assertEquals("15 Min", DurationFormat.toHuman("PT900S"))
        assertEquals("1 Std 30 Min", DurationFormat.toHuman("PT1H30M0S"))
        assertEquals("", DurationFormat.toHuman("PT0M"))
        assertEquals("", DurationFormat.toHuman("PT0S"))

        assertEquals(15, DurationFormat.minutes("PT900S"))
        assertNull(DurationFormat.minutes("PT0M"))
    }

    @Test
    fun secondBasedDurationEnglishLocale() {
        DurationFormat.setRenderer { h, m -> render(h, m, "h", "min") }
        assertEquals("15 min", DurationFormat.toHuman("PT900S"))
    }

    /** Parsing takes every bundled language at once: a German recipe read on an English phone
     *  still has to round-trip, and a French one on either. */
    @Test
    fun humanToIsoAcceptsEveryLanguage() {
        assertEquals("PT90M", DurationFormat.toIso("1 heure 30 minutes"))
        assertEquals("PT45M", DurationFormat.toIso("45 mn"))
        assertEquals("PT130M", DurationFormat.toIso("2 hours 10 mins"))
        // The longest word wins, so "minutes" is not cut short by "min".
        assertEquals("PT20M", DurationFormat.toIso("20 minutes"))
    }

    @Test
    fun humanToIso() {
        assertEquals("PT25M", DurationFormat.toIso("25 Min"))
        assertEquals("PT70M", DurationFormat.toIso("1 Std 10 Min"))
        assertEquals("PT120M", DurationFormat.toIso("2 Std"))
        assertNull(DurationFormat.toIso(""))
        assertNull(DurationFormat.toIso(null))
    }

    @Test
    fun alreadyIsoIsKept() {
        assertEquals("PT25M", DurationFormat.toIso("PT25M"))
    }

    @Test
    fun unparseableTextStoredVerbatim() {
        assertEquals("über Nacht", DurationFormat.toIso("über Nacht"))
    }

    @Test
    fun roundTrips() {
        assertEquals("PT25M", DurationFormat.toIso(DurationFormat.toHuman("PT25M")))
        assertEquals("PT70M", DurationFormat.toIso(DurationFormat.toHuman("PT1H10M")))
    }
}
