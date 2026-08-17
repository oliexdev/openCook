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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * openCook brand palette — "warm & cozy": terracotta primary, herb-green secondary,
 * warm cream surfaces. Used by default; Material You (dynamic color) is opt-in.
 */

// Light
private val Terracotta = Color(0xFF993F19)
private val TerracottaContainer = Color(0xFFF6E2D6)
private val Herb = Color(0xFF486227)
private val HerbContainer = Color(0xFFE3EBD4)
private val Gold = Color(0xFF845B16)
private val GoldContainer = Color(0xFFF3E4C7)
private val Cream = Color(0xFFFBF5EE)
private val Ink = Color(0xFF2C2118)
private val WarmMuted = Color(0xFF625647)
private val WarmOutline = Color(0xFFD8CCBE)

val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = Color(0xFF5A2008),
    secondary = Herb,
    onSecondary = Color.White,
    secondaryContainer = HerbContainer,
    onSecondaryContainer = Color(0xFF293D12),
    tertiary = Gold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = Color(0xFF422C00),
    background = Cream,
    onBackground = Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE5DA),
    onSurfaceVariant = WarmMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF5EE),
    surfaceContainer = Color(0xFFF6EEE4),
    surfaceContainerHigh = Color(0xFFEAD4C5),
    surfaceContainerHighest = Color(0xFFDCCBB5), // L* 82.5 — controls read as controls
    outline = WarmOutline,
    outlineVariant = Color(0xFFE7DCCF),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

// Dark
private val TerracottaDark = Color(0xFFEE9A6F)
private val HerbDark = Color(0xFFBDD49A)

val DarkColors = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = Color(0xFF4A1E0A),
    primaryContainer = Color(0xFF6E3318),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = HerbDark,
    onSecondary = Color(0xFF2A3D11),
    secondaryContainer = Color(0xFF3F5424),
    onSecondaryContainer = Color(0xFFD9EBB5),
    tertiary = Color(0xFFE6C079),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E4216),
    onTertiaryContainer = Color(0xFFF3E4C7),
    background = Color(0xFF1B1611),
    onBackground = Color(0xFFF3E9DE),
    surface = Color(0xFF211B15),
    onSurface = Color(0xFFF3E9DE),
    surfaceVariant = Color(0xFF4C4034),
    onSurfaceVariant = Color(0xFFD6C8B6),
    surfaceContainerLowest = Color(0xFF150F0B),
    surfaceContainerLow = Color(0xFF211B15),
    surfaceContainer = Color(0xFF271F18),
    surfaceContainerHigh = Color(0xFF332920), // L* 17.4
    surfaceContainerHighest = Color(0xFF403528), // L* 23.0
    outline = Color(0xFF9C8E7E),
    outlineVariant = Color(0xFF635546),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)
