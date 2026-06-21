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

package com.food.opencook.ui.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.food.opencook.R

/**
 * Persistent strip above the bottom navigation. Distinguishes a running scan's
 * stage from queued ones, surfaces failures (with retry) and finished recipes (by
 * name), and lets the user cancel running scans — all without leaving the screen.
 */
@Composable
fun StatusStrip(
    state: StatusStripUiState,
    onReview: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state.mode) {
        StripMode.HIDDEN -> return

        StripMode.ACTIVE -> StripSurface {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(activeText(state), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.status_cancel)) }
        }

        StripMode.FAILED -> StripSurface(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                pluralStringResource(R.plurals.status_failed, state.failedCount, state.failedCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.status_retry)) }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.status_dismiss))
            }
        }

        StripMode.FINISHED -> StripSurface(onClickRow = onReview) {
            Text(
                finishedText(state.finishedRecipeNames),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text(stringResource(R.string.status_review_action)) }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.status_dismiss))
            }
        }
    }
}

@Composable
private fun StripSurface(
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClickRow: (() -> Unit)? = null,
    rowContent: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClickRow != null) Modifier.clickable(onClick = onClickRow) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = { rowContent() },
        )
    }
}

@Composable
private fun activeText(state: StatusStripUiState): String {
    val stage = stageLabel(state.oldestStageKey, hasProcessing = state.processingCount > 0)
    return when {
        // Something is processing and others wait.
        state.processingCount > 0 && state.queuedCount > 0 ->
            stringResource(R.string.status_stage_with_queue, stage, state.queuedCount)
        // Only queued, nothing started yet.
        state.processingCount == 0 ->
            stringResource(R.string.status_queued_only, state.queuedCount)
        // Single scan processing.
        else -> stage
    }
}

@Composable
private fun finishedText(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> stringResource(R.string.status_added_one, names.first())
    else -> stringResource(R.string.status_added_many, names.first(), names.size - 1)
}

@Composable
private fun stageLabel(stageKey: String?, hasProcessing: Boolean): String = when (stageKey) {
    "reading_text" -> stringResource(R.string.stage_reading_text)
    "detecting_photos" -> stringResource(R.string.stage_detecting_photos)
    else -> if (hasProcessing) stringResource(R.string.status_processing_generic)
    else stringResource(R.string.status_queued)
}
