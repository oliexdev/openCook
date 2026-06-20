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
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.sync.SyncStatus
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
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()

    var serverUrl by remember { mutableStateOf(state.serverUrl) }
    LaunchedEffect(state.serverUrl) { serverUrl = state.serverUrl }
    var serverExpanded by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

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
            SettingsRow(
                icon = Icons.Outlined.Home,
                title = state.householdName.ifBlank { "—" },
                subtitle = stringResource(R.string.settings_household_hint),
            )
            if (state.householdCode.isNotBlank()) {
                val clipboard = LocalClipboardManager.current
                val context = LocalContext.current
                val copied = stringResource(R.string.settings_household_code_copied)
                SettingsRow(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.settings_household_code_label),
                    subtitle = state.householdCode,
                    onClick = {
                        clipboard.setText(AnnotatedString(state.householdCode))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
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
                icon = Icons.Outlined.Sync,
                title = stringResource(R.string.settings_sync_now),
                subtitle = syncStatusLabel(syncStatus),
                onClick = appBar::sync,
            )
            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = stringResource(R.string.settings_household_leave),
                onClick = { showLeaveConfirm = true },
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

            HorizontalDivider()

            // --- Server ---
            SectionHeader(
                stringResource(R.string.settings_server_section),
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
            SettingsRow(
                icon = Icons.Outlined.Dns,
                title = stringResource(R.string.settings_server_label),
                subtitle = state.serverUrl.ifBlank { stringResource(R.string.settings_server_hint) },
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
                    Button(onClick = { viewModel.saveServerUrl(serverUrl) }) {
                        Text(stringResource(R.string.settings_save))
                    }
                }
            }
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

@Composable
private fun syncStatusLabel(status: SyncStatus): String = when (status) {
    SyncStatus.NotConfigured -> stringResource(R.string.sync_status_not_configured)
    is SyncStatus.Syncing -> stringResource(R.string.sync_status_syncing)
    is SyncStatus.Failed -> stringResource(R.string.sync_status_failed)
    SyncStatus.HouseholdMissing -> stringResource(R.string.sync_status_household_missing)
    is SyncStatus.Idle -> stringResource(R.string.sync_status_ok)
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
