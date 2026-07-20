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

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Who this phone cooks with: the household's identity, its size, and the way out. */
@Composable
fun HouseholdSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLeaveConfirm by remember { mutableStateOf(false) }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.settings_leave_confirm_title)) },
            text = { Text(stringResource(R.string.settings_leave_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        viewModel.leaveHousehold()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_household_leave)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    SettingsSubScreen(stringResource(R.string.settings_household_section_label), onBack) {
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
        }
        SettingsRow(
            icon = Icons.Outlined.Group,
            title = stringResource(R.string.settings_household_size_label),
            subtitle = stringResource(R.string.settings_household_size_hint),
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
        if (joined) {
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = stringResource(R.string.settings_household_leave),
                subtitle = stringResource(R.string.settings_household_leave_hint),
                onClick = { showLeaveConfirm = true },
            )
        }
    }
}
