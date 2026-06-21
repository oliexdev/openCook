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

package com.food.opencook.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.food.opencook.R
import com.food.opencook.ui.theme.Spacing

/**
 * The frame shared by every wizard page: progress bar + label on top, the page's
 * actual content in the middle, and a persistent navigation bar at the bottom.
 * Pages decide their own scrolling behaviour and validation messages.
 */
@Composable
fun WizardScaffold(
    step: WizardStep,
    isFirst: Boolean,
    isLast: Boolean,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WizardHeader(step)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
        WizardNavBar(
            isFirst = isFirst,
            isLast = isLast,
            canAdvance = canAdvance,
            saveLabel = saveLabel,
            onBack = onBack,
            onNext = onNext,
            onSave = onSave,
        )
    }
}

@Composable
private fun WizardHeader(step: WizardStep) {
    val index = step.ordinal
    val count = WizardStep.values().size
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / count },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.wizard_step_of, index + 1, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(step.titleRes),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun WizardNavBar(
    isFirst: Boolean,
    isLast: Boolean,
    canAdvance: Boolean,
    saveLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Inset for the system navigation bar / gesture area so the buttons stay tappable
            // when the app draws edge-to-edge (targetSdk 36 default).
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onBack, enabled = !isFirst, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.wizard_back))
        }
        Button(
            onClick = if (isLast) onSave else onNext,
            enabled = canAdvance,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isLast) saveLabel else stringResource(R.string.wizard_next))
        }
    }
}

private val WizardStep.titleRes: Int
    get() = when (this) {
        WizardStep.BASICS -> R.string.wizard_step_basics
        WizardStep.INGREDIENTS -> R.string.wizard_step_ingredients
        WizardStep.STEPS -> R.string.wizard_step_steps
        WizardStep.DETAILS -> R.string.wizard_step_details
        WizardStep.SUMMARY -> R.string.wizard_step_summary
    }
