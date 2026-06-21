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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Add-row with offline autocomplete: a text field whose suggestions (filtered from
 * the user's own data + a built-in grocery list) drop down as they type, an optional
 * barcode-scan icon, and an Add button. Free typing always works — a suggestion is
 * only a shortcut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteAddField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    onAdd: () -> Unit,
    placeholder: String,
    addLabel: String,
    modifier: Modifier = Modifier,
    scanContentDescription: String? = null,
    onScan: (() -> Unit)? = null,
    autoFocus: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val showMenu = expanded && suggestions.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) focusRequester.requestFocus() }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(
            expanded = showMenu,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            TextField(
                value = value,
                onValueChange = { onValueChange(it); expanded = true },
                placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                // Enter adds the item and keeps the field focused for the next one.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onAdd() }),
                // Borderless to match the search bar — it lives inside the top app bar.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = onScan?.let {
                    {
                        IconButton(onClick = it) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = scanContentDescription)
                        }
                    }
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
                suggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = { onValueChange(suggestion); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        FilledIconButton(onClick = { onAdd(); expanded = false }, enabled = value.isNotBlank()) {
            Icon(Icons.Outlined.Add, contentDescription = addLabel)
        }
    }
}
