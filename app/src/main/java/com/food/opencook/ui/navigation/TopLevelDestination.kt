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

package com.food.opencook.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.food.opencook.R

/**
 * Top-level destinations for the adaptive navigation (bottom bar on phones,
 * rail/drawer on tablets). Scanning is contextual (add-recipe / barcode), not a tab.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    RECIPES("recipes", R.string.nav_recipes, Icons.AutoMirrored.Outlined.MenuBook),
    PLAN("plan", R.string.nav_plan, Icons.Outlined.CalendarMonth),
    SHOPPING("shopping", R.string.nav_shopping_short, Icons.Outlined.ShoppingCart),
    SETTINGS("settings", R.string.nav_more, Icons.Outlined.Tune),
}
