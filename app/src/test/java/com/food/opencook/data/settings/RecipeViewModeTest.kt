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

package com.food.opencook.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeViewModeTest {

    @Test
    fun `fromStored defaults to LIST when nothing is stored`() {
        assertEquals(RecipeViewMode.LIST, RecipeViewMode.fromStored(null))
    }

    @Test
    fun `fromStored defaults to LIST for an unrecognised value`() {
        assertEquals(RecipeViewMode.LIST, RecipeViewMode.fromStored("ALBUM"))
    }

    @Test
    fun `fromStored round-trips every real mode by name`() {
        RecipeViewMode.entries.forEach { mode ->
            assertEquals(mode, RecipeViewMode.fromStored(mode.name))
        }
    }
}
