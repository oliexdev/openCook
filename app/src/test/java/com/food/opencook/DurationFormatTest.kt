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
import java.util.Locale

class DurationFormatTest {

    private val originalLocale = Locale.getDefault()

    // toHuman() localizes its unit labels to the default locale; pin it for deterministic assertions.
    @Before fun setGerman() = Locale.setDefault(Locale.GERMAN)
    @After fun restore() = Locale.setDefault(originalLocale)

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
        Locale.setDefault(Locale.ENGLISH)
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
