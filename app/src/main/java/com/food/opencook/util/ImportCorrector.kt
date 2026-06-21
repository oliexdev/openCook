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

/**
 * Applies only the **high-confidence** (single 1-edit match) ingredient-name corrections
 * at import time, silently, before a scanned recipe is stored and synced. Lower-confidence
 * matches are left for the review screen to *suggest* (they need the user's choice).
 *
 * Implemented over the curated bundled vocabulary only (lexicon + CommonGroceries), never the
 * user's raw stored data, so a misread can't snap to another messy entry. A no-op identity
 * (`ImportCorrector { it }`) is used where correction isn't wanted (e.g. tests).
 */
fun interface ImportCorrector {
    fun correct(name: String): String
}
