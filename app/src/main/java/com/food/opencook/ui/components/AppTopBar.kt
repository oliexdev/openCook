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

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.food.opencook.R
import com.food.opencook.sync.SyncStatus
import com.food.opencook.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The unified top bar for the main (shell) screens: a title plus the shared sync
 * status indicator, then any screen-specific [actions]. Full-screen routes keep
 * their own back-bar and do not use this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    syncStatus: SyncStatus,
    onSync: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            title = { Text(title) },
            actions = {
                SyncStatusIcon(syncStatus, onSync)
                actions()
            },
            // Blend with the warm screen background instead of the default white surface.
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background,
            ),
        )
        // For a large pull, a thin determinate line under the bar with a phase-specific label.
        (syncStatus as? SyncStatus.Syncing)?.takeIf { it.fraction != null }?.let { SyncProgressBar(it) }
    }
}

/** The shared progress line shown under the top bar during a large sync. */
@Composable
private fun SyncProgressBar(state: SyncStatus.Syncing) {
    val fraction = state.fraction ?: return
    val label = when (state.phase) {
        SyncStatus.Phase.APPLY -> stringResource(R.string.sync_progress_recipes, state.count ?: 0)
        SyncStatus.Phase.IMAGES -> stringResource(
            R.string.sync_progress_images,
            state.count ?: 0,
            state.total ?: 0,
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.sync_progress_percent, (fraction * 100).roundToInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        )
    }
}

/**
 * Sync-arrows icon reflecting sync state; tap to sync now (disabled while syncing).
 * Synced is the brand tint; "no server" / "couldn't reach" are calm grey — not an
 * error, so never red. In an error state tapping also pops a snackbar spelling out
 * the problem (so the cryptic icon isn't the only signal), then still retries.
 */
@Composable
fun SyncStatusIcon(status: SyncStatus, onSync: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val errorMessage = syncErrorMessage(status)
    val onClick: () -> Unit = {
        onSync()
        errorMessage?.let { msg ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(msg)
            }
        }
    }
    when (status) {
        SyncStatus.NotConfigured -> IconButton(onClick = onClick) {
            Icon(
                Icons.Outlined.SyncDisabled,
                contentDescription = stringResource(R.string.sync_status_not_configured),
                tint = muted,
            )
        }
        is SyncStatus.Idle -> IconButton(onClick = onClick) {
            Icon(
                Icons.Outlined.Sync,
                contentDescription = lastSuccessDescription(status.lastSuccessEpochMs),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        is SyncStatus.Syncing -> IconButton(onClick = onClick, enabled = false) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        is SyncStatus.Failed -> IconButton(onClick = onClick) {
            // "Not reachable" is the normal offline state, not an error → muted, not red.
            Icon(
                Icons.Outlined.Sync,
                contentDescription = stringResource(R.string.sync_status_failed),
                tint = muted,
            )
        }
        SyncStatus.HouseholdMissing -> IconButton(onClick = onClick) {
            // A real, actionable problem (server lost our household) → flag it red.
            Icon(
                Icons.Outlined.SyncProblem,
                contentDescription = stringResource(R.string.sync_status_household_missing),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The message a tap should surface, or null when the state isn't an error worth explaining. */
@Composable
private fun syncErrorMessage(status: SyncStatus): String? = when (status) {
    is SyncStatus.Failed -> stringResource(R.string.sync_error_offline)
    SyncStatus.HouseholdMissing -> stringResource(R.string.sync_status_household_missing)
    else -> null
}

@Composable
private fun lastSuccessDescription(epochMs: Long?): String {
    if (epochMs == null) return stringResource(R.string.sync_status_ok)
    val rel = DateUtils.getRelativeTimeSpanString(
        epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    )
    return stringResource(R.string.sync_status_last_success, rel)
}
