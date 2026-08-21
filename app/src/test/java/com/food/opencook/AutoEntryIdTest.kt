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

import com.food.opencook.repository.MealPlanRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The rolling planner fills the window on whichever device happens to be opened. Two phones
 * doing that out of contact must not end up with two dinners on one day — which is why an
 * auto-filled entry derives its id from the cell rather than drawing a random one. Same cell,
 * same row id, and the sync log's per-field last-write-wins settles on one dish.
 */
class AutoEntryIdTest {

    @Test
    fun `the same cell yields the same id on any device`() {
        assertEquals(
            MealPlanRepository.autoEntryId("2026-08-20", "dinner"),
            MealPlanRepository.autoEntryId("2026-08-20", "dinner"),
        )
    }

    @Test
    fun `different meals of the same day are different rows`() {
        assertNotEquals(
            MealPlanRepository.autoEntryId("2026-08-20", "lunch"),
            MealPlanRepository.autoEntryId("2026-08-20", "dinner"),
        )
    }

    @Test
    fun `the same meal on different days are different rows`() {
        assertNotEquals(
            MealPlanRepository.autoEntryId("2026-08-20", "dinner"),
            MealPlanRepository.autoEntryId("2026-08-21", "dinner"),
        )
    }

    @Test
    fun `the id is a well-formed uuid`() {
        val id = MealPlanRepository.autoEntryId("2026-08-20", "dinner")
        assertEquals(id, java.util.UUID.fromString(id).toString())
    }
}
