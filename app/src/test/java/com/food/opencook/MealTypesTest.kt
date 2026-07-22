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

import com.food.opencook.util.MealTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The meal-type axis rests on two invariants: stored values are stable keys (any
 * language normalizes in, unknown drops out), and null/blank storage *means* the
 * "lunch + dinner" default — resolved at read time, never written back.
 */
class MealTypesTest {

    @Test
    fun normalizeKeyMapsBothLanguagesAndDropsUnknown() {
        assertEquals("breakfast", MealTypes.normalizeKey("breakfast"))
        assertEquals("breakfast", MealTypes.normalizeKey(" Frühstück "))
        assertEquals("lunch", MealTypes.normalizeKey("Mittagessen"))
        assertEquals("snack", MealTypes.normalizeKey("Kuchen"))
        assertEquals("snack", MealTypes.normalizeKey("kaffee"))
        assertEquals("dinner", MealTypes.normalizeKey("Abendbrot"))
        // Unknown values are dropped, never guessed.
        assertNull(MealTypes.normalizeKey("brunch-party"))
        assertNull(MealTypes.normalizeKey(""))
        assertNull(MealTypes.normalizeKey(null))
    }

    @Test
    fun nullAndBlankStorageMeanTheDefault() {
        assertEquals(listOf("lunch", "dinner"), MealTypes.fromStored(null))
        assertEquals(listOf("lunch", "dinner"), MealTypes.fromStored(""))
        assertEquals(listOf("lunch", "dinner"), MealTypes.fromStored("  \n "))
        // Garbage-only storage also resolves to the default.
        assertEquals(listOf("lunch", "dinner"), MealTypes.fromStored("quatsch"))
    }

    @Test
    fun fromStoredNormalizesOrdersAndDedupes() {
        // Day order (KEYS order), duplicates collapsed, aliases normalized.
        assertEquals(listOf("breakfast", "dinner"), MealTypes.fromStored("dinner\nbreakfast\ndinner"))
        assertEquals(listOf("breakfast", "snack"), MealTypes.fromStored("Kuchen\nFrühstück"))
    }

    @Test
    fun toStoredEmptyIsNullSoTheDefaultAppliesAgain() {
        assertNull(MealTypes.toStored(emptyList()))
        assertNull(MealTypes.toStored(listOf("unknown")))
        assertEquals("breakfast\nsnack", MealTypes.toStored(listOf("snack", "breakfast")))
    }

    @Test
    fun storedRoundTripIsStable() {
        val stored = MealTypes.toStored(listOf("dinner", "breakfast"))
        assertEquals(listOf("breakfast", "dinner"), MealTypes.fromStored(stored))
        assertEquals(stored, MealTypes.toStored(MealTypes.fromStored(stored)))
    }
}
