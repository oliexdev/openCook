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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.settings.ContentLanguages
import com.food.opencook.ui.theme.Spacing

/** How the app looks, and which language recipes are written in. */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val contentLanguage by viewModel.contentLanguage.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showLanguageDialog) {
        ContentLanguageDialog(
            current = contentLanguage,
            onPick = { viewModel.setContentLanguage(it); showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false },
        )
    }

    SettingsSubScreen(stringResource(R.string.settings_appearance_section), onBack) {
        SettingsRow(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_hint),
            trailing = { Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) }) },
        )
        SettingsRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_content_language),
            subtitle = contentLanguageLabel(contentLanguage),
            onClick = { showLanguageDialog = true },
            showChevron = true,
        )
    }
}

/** Human label for a content-language code (null = follow the device's system language). */
@Composable
fun contentLanguageLabel(code: String?): String = when (code) {
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
