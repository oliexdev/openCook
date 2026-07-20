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

package com.food.opencook.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.backup.BackupFormat
import com.food.opencook.data.backup.BackupPhase
import com.food.opencook.data.backup.BackupRejected
import com.food.opencook.data.backup.BackupState
import com.food.opencook.ui.components.SectionHeader
import com.food.opencook.ui.theme.Spacing

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val folderName by viewModel.folderName.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val peekError by viewModel.peekError.collectAsStateWithLifecycle()

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME),
    ) { uri -> if (uri != null) viewModel.export(uri) }

    // Some file providers hand a .zip back as octet-stream, so accept both rather than
    // hiding the user's own backup behind a filter.
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.offerRestore(uri) }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) viewModel.setAutoFolder(uri) }

    val busy = state is BackupState.Running

    pending?.let { p ->
        val counts = p.manifest.counts
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(
                            R.string.backup_restore_confirm_summary,
                            p.manifest.createdAt,
                            counts.recipes,
                            counts.images,
                        ),
                    )
                    p.manifest.householdName?.let {
                        Text(stringResource(R.string.backup_restore_confirm_household, it))
                    }
                    Text(
                        stringResource(R.string.backup_restore_confirm_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.backup_restore_action))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::cancelRestore) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    peekError?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissPeekError,
            title = { Text(stringResource(R.string.backup_not_readable_title)) },
            text = { Text(stringResource(R.string.backup_not_readable_text)) },
            confirmButton = {
                Button(onClick = viewModel::dismissPeekError) { Text(stringResource(R.string.backup_ok)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
            )

            ActionRow(
                icon = Icons.Outlined.Backup,
                title = stringResource(R.string.backup_create),
                subtitle = stringResource(R.string.backup_create_hint),
                enabled = !busy,
                onClick = { createLauncher.launch(viewModel.suggestedFileName()) },
            )
            ActionRow(
                icon = Icons.Outlined.Restore,
                title = stringResource(R.string.backup_restore),
                subtitle = stringResource(R.string.backup_restore_hint),
                enabled = !busy,
                onClick = { openLauncher.launch(arrayOf(BackupFormat.MIME, "application/octet-stream")) },
            )

            if (busy) {
                val running = state as BackupState.Running
                Column(
                    Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(phaseLabel(running.phase, running.restoring), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { running.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ResultCard(state, viewModel::clearResult)

            HorizontalDivider()

            SectionHeader(
                stringResource(R.string.backup_auto_section),
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
            ActionRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.backup_auto_title),
                subtitle = stringResource(R.string.backup_auto_hint),
                enabled = true,
                onClick = {
                    if (config.enabled) viewModel.setAutoFolder(null) else folderLauncher.launch(null)
                },
                trailing = {
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { on ->
                            if (on) folderLauncher.launch(null) else viewModel.setAutoFolder(null)
                        },
                    )
                },
            )
            if (config.enabled) {
                ActionRow(
                    icon = Icons.Outlined.Folder,
                    title = folderName ?: stringResource(R.string.backup_auto_folder_unknown),
                    subtitle = stringResource(R.string.backup_auto_folder_hint, config.keep),
                    enabled = true,
                    onClick = { folderLauncher.launch(null) },
                )
                config.lastError?.let {
                    Text(
                        stringResource(R.string.backup_auto_last_error, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(state: BackupState, onDismiss: () -> Unit) {
    val message = when (state) {
        is BackupState.Exported -> stringResource(
            R.string.backup_export_done,
            state.manifest.counts.recipes,
            state.manifest.counts.images,
        )
        is BackupState.Restored -> stringResource(
            R.string.backup_restore_done,
            state.result.recipesImported,
            state.result.imagesRestored,
        )
        is BackupState.Failed -> when (val reason = state.reason) {
            is BackupRejected.TooNew -> stringResource(R.string.backup_error_too_new)
            is BackupRejected.Unsafe -> stringResource(R.string.backup_error_unsafe)
            BackupRejected.NotABackup -> stringResource(R.string.backup_not_readable_text)
            null -> stringResource(R.string.backup_error_generic, state.message.orEmpty())
        }
        else -> null
    } ?: return

    Card(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
        Column(
            Modifier.padding(Spacing.md).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(message, textAlign = TextAlign.Start)
            OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.backup_ok))
            }
        }
    }
}

@Composable
private fun phaseLabel(phase: BackupPhase, restoring: Boolean): String = when {
    restoring && phase == BackupPhase.IMAGES -> stringResource(R.string.backup_phase_restore_images)
    restoring -> stringResource(R.string.backup_phase_restore_recipes)
    phase == BackupPhase.IMAGES -> stringResource(R.string.backup_phase_images)
    phase == BackupPhase.LISTS -> stringResource(R.string.backup_phase_lists)
    else -> stringResource(R.string.backup_phase_recipes)
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (trailing == null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
            )
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}
