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

package com.food.opencook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * openCook type scale — clear hierarchy with bold, friendly headlines and a
 * comfortable 16sp reading size for recipe steps/ingredients (cook-from-screen).
 * Built on the platform font; weights/sizes tuned per the redesign.
 */
private val base = Typography()

val Typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * Grow/shrink a style by the user's size factor. `letterSpacing` is deliberately left
 * alone: the M3 tracking values are optically tuned, and relatively tighter tracking is
 * what you want as the size goes up.
 */
private fun TextStyle.scaled(factor: Float) = copy(
    fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
)

/**
 * The type scale at the user's chosen size factor (see `FontScales`). Because no screen
 * hardcodes an `sp` value — everything reads `MaterialTheme.typography` — scaling here is
 * all it takes to resize the whole app.
 *
 * This multiplies on top of the system font size rather than replacing it: `sp` still
 * resolves through the platform's own (since Android 14, non-linear) scaling, so a user
 * who enlarged text device-wide keeps that on top of whatever they pick here.
 */
fun Typography.scaled(factor: Float): Typography =
    if (factor == 1f) this else copy(
        displayLarge = displayLarge.scaled(factor),
        displayMedium = displayMedium.scaled(factor),
        displaySmall = displaySmall.scaled(factor),
        headlineLarge = headlineLarge.scaled(factor),
        headlineMedium = headlineMedium.scaled(factor),
        headlineSmall = headlineSmall.scaled(factor),
        titleLarge = titleLarge.scaled(factor),
        titleMedium = titleMedium.scaled(factor),
        titleSmall = titleSmall.scaled(factor),
        bodyLarge = bodyLarge.scaled(factor),
        bodyMedium = bodyMedium.scaled(factor),
        bodySmall = bodySmall.scaled(factor),
        labelLarge = labelLarge.scaled(factor),
        labelMedium = labelMedium.scaled(factor),
        labelSmall = labelSmall.scaled(factor),
    )
