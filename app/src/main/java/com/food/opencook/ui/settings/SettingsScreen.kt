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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.BuildConfig
import com.food.opencook.R
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.theme.Spacing

/**
 * Settings hub. Every entry opens a sub-page; the subtitles carry the current state, so
 * the hub answers "how is this phone set up?" at a glance instead of being a bare menu.
 */
@Composable
fun SettingsScreen(
    onOpenHousehold: () -> Unit = {},
    onOpenSync: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val contentLanguage by viewModel.contentLanguage.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val p2pEnabled by viewModel.p2pEnabled.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackupLabel.collectAsStateWithLifecycle()
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()

    val joined = state.householdCode.isNotBlank()

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.settings_title), syncStatus, appBar::sync) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
        ) {
            SettingsRow(
                icon = Icons.Outlined.Home,
                title = stringResource(R.string.settings_household_section_label),
                subtitle = if (joined) {
                    stringResource(
                        R.string.settings_hub_household_summary,
                        state.householdName.ifBlank { "—" },
                        state.householdSize,
                    )
                } else {
                    stringResource(R.string.settings_hub_household_local_only)
                },
                onClick = onOpenHousehold,
                showChevron = true,
            )
            // Sync is only meaningful once a household exists — without one there is
            // nothing and nobody to sync with.
            if (joined) {
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.settings_sync_section),
                    subtitle = syncSummary(state.serverUrl, p2pEnabled),
                    onClick = onOpenSync,
                    showChevron = true,
                )
            } else {
                SettingsRow(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.settings_connect_server),
                    subtitle = stringResource(R.string.settings_connect_server_hint),
                    onClick = { viewModel.connectToServer() },
                    showChevron = true,
                )
            }
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_appearance_section),
                subtitle = stringResource(
                    R.string.settings_hub_appearance_summary,
                    stringResource(
                        if (dynamicColor) R.string.settings_dynamic_color_on else R.string.settings_dynamic_color_off,
                    ),
                    contentLanguageLabel(contentLanguage),
                    fontSizeLabel(fontScale),
                ),
                onClick = onOpenAppearance,
                showChevron = true,
            )
            if (joined) {
                SettingsRow(
                    icon = Icons.Outlined.Backup,
                    title = stringResource(R.string.settings_backup),
                    subtitle = lastBackup ?: stringResource(R.string.settings_backup_subtitle),
                    onClick = onOpenBackup,
                    showChevron = true,
                )
            }
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_hub_about_summary, BuildConfig.VERSION_NAME),
                onClick = onOpenAbout,
                showChevron = true,
            )
        }
    }
}

/** Where this phone's data actually goes: a server, other phones, both, or nowhere. */
@Composable
private fun syncSummary(serverUrl: String, p2pEnabled: Boolean): String {
    val hasServer = serverUrl.isNotBlank()
    return when {
        hasServer && p2pEnabled -> stringResource(R.string.settings_hub_sync_server_and_peers)
        hasServer -> stringResource(R.string.settings_hub_sync_server_only)
        p2pEnabled -> stringResource(R.string.settings_hub_sync_peers_only)
        else -> stringResource(R.string.settings_hub_sync_off)
    }
}
