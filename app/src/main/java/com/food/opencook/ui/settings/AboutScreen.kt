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

package com.food.opencook.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.food.opencook.BuildConfig
import com.food.opencook.R

private const val REPO_URL = "https://github.com/oliexdev/openCook"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val SPONSOR_URL = "https://github.com/sponsors/oliexdev"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun open(url: String) {
        // A phone with no browser is unusual but possible (kiosk/work profile) — don't crash.
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    SettingsSubScreen(stringResource(R.string.settings_about), onBack) {
        SettingsIntro(stringResource(R.string.about_intro))

        SettingsRow(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.about_version),
            subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
        SettingsRow(
            icon = Icons.Outlined.Gavel,
            title = stringResource(R.string.about_license),
            subtitle = stringResource(R.string.about_license_subtitle),
            onClick = { open(LICENSE_URL) },
            showChevron = true,
        )
        SettingsRow(
            icon = Icons.Outlined.Code,
            title = stringResource(R.string.about_source),
            subtitle = stringResource(R.string.about_source_subtitle),
            onClick = { open(REPO_URL) },
            showChevron = true,
        )
        SettingsRow(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.about_privacy),
            subtitle = stringResource(R.string.about_privacy_subtitle),
        )
        SettingsRow(
            icon = Icons.Outlined.Favorite,
            title = stringResource(R.string.about_support),
            subtitle = stringResource(R.string.about_support_subtitle),
            onClick = { open(SPONSOR_URL) },
            showChevron = true,
        )
    }
}
