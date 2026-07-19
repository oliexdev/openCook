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

import com.food.opencook.data.settings.RecipeViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipesLayoutTest {

    @Test
    fun `list mode is one column on a phone width`() {
        assertEquals(1, RecipesLayout.columnsFor(360f, RecipeViewMode.LIST))
    }

    @Test
    fun `list mode widens to two then three columns on larger screens`() {
        assertEquals(2, RecipesLayout.columnsFor(700f, RecipeViewMode.LIST))
        assertEquals(3, RecipesLayout.columnsFor(1000f, RecipeViewMode.LIST))
    }

    @Test
    fun `grid mode is always at least two columns, even on a phone`() {
        assertEquals(2, RecipesLayout.columnsFor(360f, RecipeViewMode.GRID))
    }

    @Test
    fun `grid mode widens to three then four columns on larger screens`() {
        assertEquals(3, RecipesLayout.columnsFor(700f, RecipeViewMode.GRID))
        assertEquals(4, RecipesLayout.columnsFor(1000f, RecipeViewMode.GRID))
    }

    @Test
    fun `breakpoints match exactly at the boundary width`() {
        // < 600 is the phone tier; exactly 600 already counts as the wider tier.
        assertEquals(1, RecipesLayout.columnsFor(599.9f, RecipeViewMode.LIST))
        assertEquals(2, RecipesLayout.columnsFor(600f, RecipeViewMode.LIST))
    }
}
