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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.ClipData
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.food.opencook.R
import com.food.opencook.data.settings.ContentLanguages
import com.food.opencook.data.settings.TextScale
import com.food.opencook.sync.SyncStatus
import com.food.opencook.sync.SyncVia
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.SectionHeader
import com.food.opencook.ui.theme.Spacing

@Composable
fun SettingsScreen(
    onOpenAdmin: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val contentLanguage by viewModel.contentLanguage.collectAsStateWithLifecycle()
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var serverUrl by remember { mutableStateOf(state.serverUrl) }
    LaunchedEffect(state.serverUrl) { serverUrl = state.serverUrl }
    var serverExpanded by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTextScaleDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        ContentLanguageDialog(
            current = contentLanguage,
            onPick = { viewModel.setContentLanguage(it); showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showTextScaleDialog) {
        TextScaleDialog(
            current = textScale,
            onPick = { viewModel.setTextScale(it); showTextScaleDialog = false },
            onDismiss = { showTextScaleDialog = false },
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.settings_leave_confirm_title)) },
            text = { Text(stringResource(R.string.settings_leave_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = { showLeaveConfirm = false; viewModel.leaveHousehold() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.settings_household_leave)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

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
            // --- Haushalt ---
            SectionHeader(
                stringResource(R.string.settings_household_section_label),
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
            val joined = state.householdCode.isNotBlank()
            if (joined) {
                SettingsRow(
                    icon = Icons.Outlined.Home,
                    title = state.householdName.ifBlank { "—" },
                    subtitle = stringResource(R.string.settings_household_hint),
                )
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val copied = stringResource(R.string.settings_household_code_copied)
                SettingsRow(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.settings_household_code_label),
                    subtitle = state.householdCode,
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("openCook", state.householdCode)))
                        }
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                )
            } else {
                // Local-only mode: offer the documented "connect later" path. Tapping sends
                // the app back to onboarding (server → household); local data syncs up on join.
                SettingsRow(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.settings_connect_server),
                    subtitle = stringResource(R.string.settings_connect_server_hint),
                    onClick = { viewModel.connectToServer() },
                    showChevron = true,
                )
            }
            SettingsRow(
                icon = Icons.Outlined.Group,
                title = stringResource(R.string.settings_household_size_label),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(
                            onClick = { viewModel.setHouseholdSize(state.householdSize - 1) },
                            enabled = state.householdSize > 1,
                        ) { Text("−") }
                        Text("${state.householdSize}", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { viewModel.setHouseholdSize(state.householdSize + 1) }) { Text("+") }
                    }
                },
            )
            SettingsRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_content_language),
                subtitle = contentLanguageLabel(contentLanguage),
                onClick = { showLanguageDialog = true },
                showChevron = true,
            )
            if (joined) {
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = stringResource(R.string.settings_sync_now),
                    subtitle = syncStatusLabel(syncStatus),
                    onClick = appBar::sync,
                )
                val p2pEnabled by viewModel.p2pEnabled.collectAsStateWithLifecycle()
                SettingsRow(
                    icon = Icons.Outlined.Devices,
                    title = stringResource(R.string.settings_p2p_title),
                    subtitle = stringResource(R.string.settings_p2p_subtitle),
                    trailing = { Switch(checked = p2pEnabled, onCheckedChange = { viewModel.setP2pEnabled(it) }) },
                )
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = stringResource(R.string.settings_household_leave),
                    onClick = { showLeaveConfirm = true },
                )
            }

            HorizontalDivider()

            // --- Backup & Export ---
            SectionHeader(
                stringResource(R.string.settings_export_section_label),
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
            SettingsRow(
                icon = Icons.Outlined.FileDownload,
                title = stringResource(R.string.settings_export_all_data),
                subtitle = stringResource(R.string.settings_export_all_data_hint),
                onClick = { viewModel.exportData() },
            )

            HorizontalDivider()

            // --- Darstellung ---
            SectionHeader(
                stringResource(R.string.settings_appearance_section),
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_dynamic_color),
                trailing = { Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) }) },
            )
            SettingsRow(
                icon = Icons.Outlined.TextFields,
                title = stringResource(R.string.settings_text_size),
                subtitle = textScaleLabel(textScale),
                onClick = { showTextScaleDialog = true },
                showChevron = true,
            )

            // --- Server --- (only meaningful once a household/server is in use; hidden in
            // local-only mode, where the path to a server is the "Connect to a server" row above)
            if (joined) {
                HorizontalDivider()

                SectionHeader(
                    stringResource(R.string.settings_server_section),
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
                // A blank URL on a joined household = founded serverless (phones sync
                // directly). Offer attaching a server; admin (backup/restore) only makes
                // sense once one exists.
                val serverless = state.serverUrl.isBlank()
                SettingsRow(
                    icon = Icons.Outlined.Dns,
                    title = if (serverless) {
                        stringResource(R.string.settings_attach_server)
                    } else {
                        stringResource(R.string.settings_server_label)
                    },
                    subtitle = if (serverless) {
                        stringResource(R.string.settings_attach_server_hint)
                    } else {
                        state.serverUrl
                    },
                    onClick = { serverExpanded = !serverExpanded },
                )
                AnimatedVisibility(serverExpanded) {
                    Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                                if (serverless) {
                                    stringResource(R.string.settings_attach_server_action)
                                } else {
                                    stringResource(R.string.settings_save)
                                },
                            )
                        }
                    }
                }
                if (!serverless) {
                    SettingsRow(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.settings_admin),
                        subtitle = stringResource(R.string.settings_admin_subtitle),
                        onClick = onOpenAdmin,
                        showChevron = true,
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

/** Human label for a content-language code (null = follow the device's system language). */
@Composable
private fun contentLanguageLabel(code: String?): String = when (code) {
    null, "" -> stringResource(R.string.settings_content_language_system)
    "de" -> stringResource(R.string.lang_german)
    "en" -> stringResource(R.string.lang_english)
    else -> code.uppercase()
}

/** Picker for the household-wide recipe content language. */
@Composable
private fun ContentLanguageDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    // "Follow system" (null) plus every bundled content language — single source of truth in
    // SettingsRepository.CONTENT_LANGUAGES, the same list LocalizedLists loads its lexicons from.
    val codes: List<String?> = listOf(null) + ContentLanguages.CODES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_content_language)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_content_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                codes.forEach { code ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(code) }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        RadioButton(selected = current == code, onClick = { onPick(code) })
                        Text(contentLanguageLabel(code))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.processing_cancel)) }
        },
    )
}

@Composable
private fun textScaleLabel(scale: TextScale): String = when (scale) {
    TextScale.NORMAL -> stringResource(R.string.settings_text_size_normal)
    TextScale.LARGE -> stringResource(R.string.settings_text_size_large)
    TextScale.EXTRA_LARGE -> stringResource(R.string.settings_text_size_extra_large)
}

/** Picker for the global text-size step — mainly for reading recipe steps while cooking. */
@Composable
private fun TextScaleDialog(current: TextScale, onPick: (TextScale) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_text_size)) },
        text = {
            Column {
                TextScale.entries.forEach { scale ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(scale) }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        RadioButton(selected = current == scale, onClick = { onPick(scale) })
                        Text(textScaleLabel(scale))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.processing_cancel)) }
        },
    )
}

/** One settings entry: leading icon, title (+ optional subtitle), and either a
 * trailing control or a chevron when it opens something. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
