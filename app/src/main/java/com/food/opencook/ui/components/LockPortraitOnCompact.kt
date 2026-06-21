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

package com.food.opencook.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Pins the activity to portrait while this composable is in the composition, but only on
 * phone-sized displays (smallestScreenWidthDp < 600dp). On tablets it does nothing, so the
 * landscape layout keeps working. The previous orientation is restored on dispose.
 *
 * Used on the shopping list: one-handed at the supermarket a phone shouldn't flip to a
 * cramped landscape just because it tilted in your hand.
 */
@Composable
fun LockPortraitOnCompact() {
    val context = LocalContext.current
    // smallestScreenWidthDp is rotation-invariant, so this classifies the device (phone vs.
    // tablet) rather than the current orientation.
    val isCompact = LocalConfiguration.current.smallestScreenWidthDp < 600
    val activity = context as? Activity
    DisposableEffect(activity, isCompact) {
        if (activity != null && isCompact) {
            val previous = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onDispose { activity.requestedOrientation = previous }
        } else {
            onDispose { }
        }
    }
}
