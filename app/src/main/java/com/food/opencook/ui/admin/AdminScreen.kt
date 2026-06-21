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

package com.food.opencook.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.remote.dto.BackupInfoDto
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.ui.components.SectionHeader
import com.food.opencook.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRestore by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDeleteHousehold by remember { mutableStateOf<HouseholdSummaryDto?>(null) }

    val backupCreated = stringResource(R.string.admin_backup_created)
    val restoreDone = stringResource(R.string.admin_restore_done)
    val resetDone = stringResource(R.string.admin_reset_done)
    val householdDeleted = stringResource(R.string.admin_household_deleted)
    LaunchedEffect(state.info, state.error) {
        when {
            state.error != null -> { snackbar.showSnackbar(state.error!!); viewModel.clearMessages() }
            state.info == "backup_created" -> { snackbar.showSnackbar(backupCreated); viewModel.clearMessages() }
            state.info == "restore_done" -> { snackbar.showSnackbar(restoreDone); viewModel.clearMessages() }
            state.info == "reset_done" -> { snackbar.showSnackbar(resetDone); viewModel.clearMessages() }
            state.info == "household_deleted" -> { snackbar.showSnackbar(householdDeleted); viewModel.clearMessages() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.noServer -> Text(
                    stringResource(R.string.admin_not_configured_server),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                )
                !state.unlocked -> UnlockForm(
                    configured = state.configured,
                    busy = state.busy,
                    onUnlock = viewModel::unlock,
                )
                else -> AdminContent(
                    backups = state.backups,
                    households = state.households,
                    busy = state.busy,
                    onCreate = viewModel::createBackup,
                    onRestore = { confirmRestore = it },
                    onReset = { confirmReset = true },
                    onDeleteHousehold = { confirmDeleteHousehold = it },
                )
            }
        }
    }

    confirmRestore?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            icon = { Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = null) },
            title = { Text(stringResource(R.string.admin_restore_confirm_title)) },
            text = { Text(stringResource(R.string.admin_restore_confirm_text)) },
            confirmButton = {
                Button(onClick = { viewModel.restore(id); confirmRestore = null }) {
                    Text(stringResource(R.string.admin_restore))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.admin_reset_confirm_title)) },
            text = { Text(stringResource(R.string.admin_reset_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = { confirmReset = false; viewModel.resetDatabase() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.admin_reset_button)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    confirmDeleteHousehold?.let { household ->
        AlertDialog(
            onDismissRequest = { confirmDeleteHousehold = null },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.admin_delete_household_confirm_title)) },
            text = { Text(stringResource(R.string.admin_delete_household_confirm_text, household.name)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteHousehold(household.id); confirmDeleteHousehold = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.admin_delete_household_button)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDeleteHousehold = null }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }
}

@Composable
private fun UnlockForm(configured: Boolean, busy: Boolean, onUnlock: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xl).size(48.dp),
        )
        Text(
            stringResource(if (configured) R.string.admin_enter_password_title else R.string.admin_set_password_title),
            style = MaterialTheme.typography.titleLarge,
        )
        if (!configured) {
            Text(
                stringResource(R.string.admin_set_password_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = pw,
            onValueChange = { pw = it },
            label = { Text(stringResource(R.string.admin_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onUnlock(pw) },
            enabled = !busy && pw.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (configured) R.string.admin_unlock else R.string.admin_save_password))
        }
    }
}

@Composable
private fun AdminContent(
    backups: List<BackupInfoDto>,
    households: List<HouseholdSummaryDto>,
    busy: Boolean,
    onCreate: () -> Unit,
    onRestore: (String) -> Unit,
    onReset: () -> Unit,
    onDeleteHousehold: (HouseholdSummaryDto) -> Unit,
) {
    val context = LocalContext.current
    var householdsExpanded by remember { mutableStateOf(false) }
    var backupsExpanded by remember { mutableStateOf(false) }
    // Show newest first; backups already arrive newest-first, households oldest-first.
    val householdsNewest = remember(households) { households.reversed() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // --- Households (first) ---
        item { SectionHeader(stringResource(R.string.admin_households_section)) }
        item {
            GroupCard {
                if (householdsNewest.isEmpty()) {
                    EmptyRow(stringResource(R.string.admin_no_households))
                } else {
                    val shown = if (householdsExpanded) householdsNewest else householdsNewest.take(COLLAPSED)
                    shown.forEach { household ->
                        ListItem(
                            colors = transparentListColors(),
                            leadingContent = {
                                Icon(Icons.Outlined.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            headlineContent = { Text(household.name.ifBlank { "—" }) },
                            supportingContent = {
                                Text(stringResource(R.string.onboarding_persons, household.settings.householdSize))
                            },
                            trailingContent = {
                                IconButton(onClick = { onDeleteHousehold(household) }, enabled = !busy) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.admin_delete_household_button),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                    ExpandRow(householdsNewest.size, householdsExpanded) { householdsExpanded = !householdsExpanded }
                }
            }
        }

        // --- Backups ---
        item {
            SectionHeader(
                stringResource(R.string.admin_backups_section),
                modifier = Modifier.padding(top = Spacing.md),
                trailing = {
                    FilledTonalButton(onClick = onCreate, enabled = !busy) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.admin_create_backup),
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                },
            )
        }
        item {
            GroupCard {
                if (backups.isEmpty()) {
                    EmptyRow(stringResource(R.string.admin_no_backups))
                } else {
                    val shown = if (backupsExpanded) backups else backups.take(COLLAPSED)
                    shown.forEach { backup ->
                        ListItem(
                            colors = transparentListColors(),
                            leadingContent = {
                                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            headlineContent = { Text(formatBackupDate(context, backup.createdAt) ?: backup.id) },
                            supportingContent = { Text("${backup.sizeBytes / 1024} KB") },
                            trailingContent = {
                                IconButton(onClick = { onRestore(backup.id) }, enabled = !busy) {
                                    Icon(
                                        Icons.Outlined.SettingsBackupRestore,
                                        contentDescription = stringResource(R.string.admin_restore),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                    ExpandRow(backups.size, backupsExpanded) { backupsExpanded = !backupsExpanded }
                }
            }
        }

        // --- Maintenance / danger zone ---
        item {
            SectionHeader(
                stringResource(R.string.admin_maintenance_section),
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        item {
            OutlinedButton(
                onClick = onReset,
                enabled = !busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.admin_reset), modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

/** Number of rows shown before a list collapses behind "show all". */
private const val COLLAPSED = 4

/** A trailing "show all (N)" / "show less" toggle, only when a list exceeds [COLLAPSED]. */
@Composable
private fun ExpandRow(total: Int, expanded: Boolean, onToggle: () -> Unit) {
    if (total <= COLLAPSED) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (expanded) stringResource(R.string.admin_show_less)
                else stringResource(R.string.admin_show_all, total),
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

/** Format the server's ISO `created_at` to the device's locale date+time, or null on failure. */
private fun formatBackupDate(context: android.content.Context, iso: String?): String? = runCatching {
    val millis = java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    android.text.format.DateUtils.formatDateTime(
        context,
        millis,
        android.text.format.DateUtils.FORMAT_SHOW_DATE or
            android.text.format.DateUtils.FORMAT_SHOW_TIME or
            android.text.format.DateUtils.FORMAT_SHOW_YEAR,
    )
}.getOrNull()

/** A rounded surface-container card grouping related list rows. */
@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) { content() }
    }
}

/** ListItem colors that let the surrounding [GroupCard] container color show through. */
@Composable
private fun transparentListColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun EmptyRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
    )
}
