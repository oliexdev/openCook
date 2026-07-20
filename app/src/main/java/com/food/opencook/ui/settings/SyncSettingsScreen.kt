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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.sync.SyncStatus
import com.food.opencook.sync.SyncVia
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.theme.Spacing

/**
 * Everything about data moving between devices, in one place: the manual trigger, the
 * phone-to-phone switch, and the server that may or may not exist.
 *
 * The server used to be its own settings section, which overstated it — a server is one
 * possible sync target, not a separate topic.
 */
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val p2pEnabled by viewModel.p2pEnabled.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()

    var serverUrl by remember { mutableStateOf(state.serverUrl) }
    LaunchedEffect(state.serverUrl) { serverUrl = state.serverUrl }
    var serverExpanded by remember { mutableStateOf(false) }

    SettingsSubScreen(stringResource(R.string.settings_sync_section), onBack) {
        SettingsIntro(stringResource(R.string.settings_sync_intro))

        SettingsRow(
            icon = Icons.Outlined.Sync,
            title = stringResource(R.string.settings_sync_now),
            subtitle = syncStatusLabel(syncStatus),
            onClick = appBar::sync,
        )
        SettingsRow(
            icon = Icons.Outlined.Devices,
            title = stringResource(R.string.settings_p2p_title),
            subtitle = stringResource(R.string.settings_p2p_subtitle),
            trailing = { Switch(checked = p2pEnabled, onCheckedChange = { viewModel.setP2pEnabled(it) }) },
        )

        // A blank URL on a joined household = founded serverless (phones sync directly).
        val serverless = state.serverUrl.isBlank()
        SettingsRow(
            icon = Icons.Outlined.Dns,
            title = stringResource(
                if (serverless) R.string.settings_attach_server else R.string.settings_server_label,
            ),
            subtitle = if (serverless) stringResource(R.string.settings_attach_server_hint) else state.serverUrl,
            onClick = { serverExpanded = !serverExpanded },
        )
        AnimatedVisibility(serverExpanded) {
            Column(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.settings_server_url_label)) },
                    placeholder = { Text(stringResource(R.string.settings_server_url_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (serverless) viewModel.attachServer(serverUrl) else viewModel.saveServerUrl(serverUrl)
                    },
                    enabled = !busy,
                ) {
                    Text(
                        stringResource(
                            if (serverless) R.string.settings_attach_server_action else R.string.settings_save,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun syncStatusLabel(status: SyncStatus): String = when (status) {
    SyncStatus.NotConfigured -> stringResource(R.string.sync_status_not_configured)
    is SyncStatus.Syncing -> stringResource(R.string.sync_status_syncing)
    is SyncStatus.Failed -> stringResource(R.string.sync_status_failed)
    SyncStatus.HouseholdMissing -> stringResource(R.string.sync_status_household_missing)
    is SyncStatus.Idle -> when (val via = status.via) {
        // Name the peer phone so it's visible the data came phone-to-phone, not via server.
        is SyncVia.Peer -> stringResource(R.string.sync_status_ok_peer, via.name)
        else -> stringResource(R.string.sync_status_ok)
    }
}
