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

/**
 * How many grid columns the recipe list uses at a given content width, for each
 * [RecipeViewMode]. Kept as a plain function (not inline in the Composable) so the
 * width breakpoints are unit-testable without pulling in Compose UI test infra.
 */
object RecipesLayout {

    /** [maxWidthDp] is the content width in dp (e.g. from BoxWithConstraints.maxWidth.value) —
     *  the real column width after any nav rail, not the raw screen width. */
    fun columnsFor(maxWidthDp: Float, mode: RecipeViewMode): Int = when (mode) {
        // Existing behaviour: one wide tile per row on a phone, more on tablets.
        RecipeViewMode.LIST -> when {
            maxWidthDp < 600f -> 1
            maxWidthDp < 900f -> 2
            else -> 3
        }
        // Album grid always shows at least 2 columns, even on a phone — that's the point
        // of choosing this mode over the default list.
        RecipeViewMode.GRID -> when {
            maxWidthDp < 600f -> 2
            maxWidthDp < 900f -> 3
            else -> 4
        }
    }
}
