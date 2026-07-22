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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.settings.FontScales
import com.food.opencook.ui.theme.Spacing
import kotlin.math.roundToInt

/** How the app looks. */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    var showFontSizeDialog by remember { mutableStateOf(false) }

    if (showFontSizeDialog) {
        FontSizeDialog(
            current = fontScale,
            onPick = viewModel::setFontScale,
            onDismiss = { showFontSizeDialog = false },
        )
    }

    SettingsSubScreen(stringResource(R.string.settings_appearance_section), onBack) {
        SettingsRow(
            icon = Icons.Outlined.FormatSize,
            title = stringResource(R.string.settings_font_size),
            subtitle = fontSizeLabel(fontScale),
            onClick = { showFontSizeDialog = true },
            showChevron = true,
        )
        SettingsRow(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_hint),
            trailing = { Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) }) },
        )
    }
}

/**
 * Step names, aligned index-for-index with [FontScales.STEPS] — keep both lists in sync.
 */
private val FONT_SIZE_LABELS = listOf(
    R.string.settings_font_size_small,
    R.string.settings_font_size_default,
    R.string.settings_font_size_large,
    R.string.settings_font_size_larger,
    R.string.settings_font_size_largest,
)

/** Human label for a text size factor, for the settings hub's summary line. */
@Composable
fun fontSizeLabel(scale: Float): String = stringResource(FONT_SIZE_LABELS[FontScales.indexOf(scale)])

/**
 * Text size picker: a notched slider, same dialog shape as the language picker so the
 * settings list keeps its one-row-per-setting rhythm. There is deliberately no preview
 * pane — the setting applies live, so the app behind the dialog *is* the preview.
 */
@Composable
private fun FontSizeDialog(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.settings_font_size)
    // Slider position is local state, committed on release: dragging would otherwise
    // re-type the whole app on every pixel moved.
    var position by remember { mutableFloatStateOf(FontScales.indexOf(current).toFloat()) }
    val step = position.roundToInt().coerceIn(FontScales.STEPS.indices)
    val stepName = stringResource(FONT_SIZE_LABELS[step])

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("A", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = position,
                        onValueChange = { position = it },
                        onValueChangeFinished = { onPick(FontScales.STEPS[position.roundToInt()]) },
                        valueRange = 0f..(FontScales.STEPS.size - 1).toFloat(),
                        steps = FontScales.STEPS.size - 2, // notches *between* the ends
                        modifier = Modifier
                            .weight(1f)
                            // Announce "Large", not "3 of 5".
                            .semantics { contentDescription = title; stateDescription = stepName },
                        // The default inactive track is `secondaryContainer` — herb green next
                        // to the terracotta fill, which reads as two competing accents. A
                        // neutral groove keeps a single accent, and the notch dots go with it:
                        // the handle and the step name below already say where you are.
                        colors = SliderDefaults.colors(
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                    )
                    Text("A", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    stepName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

