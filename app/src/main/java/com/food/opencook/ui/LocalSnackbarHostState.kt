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

package com.food.opencook.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

/**
 * App-wide snackbar host, provided by [MainScaffold] so any screen — and the shared
 * top bar — can surface a message without threading the state through every call.
 * The default standalone instance keeps previews/tests from crashing (messages just
 * have no host to render them).
 */
val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }
