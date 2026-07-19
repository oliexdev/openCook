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

import com.food.opencook.data.settings.TextScale
import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {

    @Test
    fun `fromStored defaults to NORMAL when nothing is stored`() {
        assertEquals(TextScale.NORMAL, TextScale.fromStored(null))
    }

    @Test
    fun `fromStored defaults to NORMAL for an unrecognised value`() {
        // Guards against a future rename/removal of a step leaving old DataStore values unreadable.
        assertEquals(TextScale.NORMAL, TextScale.fromStored("HUGE"))
    }

    @Test
    fun `fromStored round-trips every real step by name`() {
        TextScale.entries.forEach { scale ->
            assertEquals(scale, TextScale.fromStored(scale.name))
        }
    }

    @Test
    fun `multipliers increase with each step`() {
        assertEquals(1.0f, TextScale.NORMAL.multiplier)
        assert(TextScale.LARGE.multiplier > TextScale.NORMAL.multiplier)
        assert(TextScale.EXTRA_LARGE.multiplier > TextScale.LARGE.multiplier)
    }
}
