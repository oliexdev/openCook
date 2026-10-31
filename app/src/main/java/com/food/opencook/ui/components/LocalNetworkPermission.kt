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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.food.opencook.data.discovery.LocalNetworkAccess

/**
 * Remembers a launcher for Android 17's local-network permission. [onResult] gets whether
 * it is granted afterwards; below Android 17 it answers `true` without any dialog.
 */
@Composable
fun rememberLocalNetworkRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        currentOnResult(granted)
    }
    return remember(launcher) {
        {
            if (LocalNetworkAccess.REQUIRED) launcher.launch(LocalNetworkAccess.PERMISSION)
            else currentOnResult(true)
        }
    }
}

/** The shell's request (see OpenCookApp), so the top-bar sync icon can offer it on tap. */
val LocalRequestLocalNetwork = staticCompositionLocalOf<() -> Unit> { {} }

/** After the user denied twice the system stops showing the dialog — the app's settings
 *  page ("Permissions → Nearby devices") is then the only way back. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
