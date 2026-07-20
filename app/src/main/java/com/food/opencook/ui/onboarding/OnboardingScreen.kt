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

package com.food.opencook.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.discovery.DiscoveredRole
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.ui.theme.Spacing

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Reset transient state each time onboarding appears (the VM is host-scoped and
    // reused after leaving a household).
    LaunchedEffect(Unit) { viewModel.onEnter() }

    // Hardware back walks the steps backwards instead of leaving the app.
    BackHandler(enabled = state.step != OnboardingStep.MODE) { viewModel.back() }

    // Onboarding renders outside MainScaffold, so unlike the main screens nothing paints a
    // background here. Surface paints colorScheme.background AND sets the matching content
    // colour, so text always contrasts (light/dark) instead of inheriting a mismatched one.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                // Onboarding is drawn directly by OpenCookApp (outside MainScaffold), so it must
                // inset itself below the status bar / camera cutout — otherwise the title draws
                // under the clock. safeDrawing also keeps it clear of the gesture nav bar.
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Hero()

            when (state.step) {
                OnboardingStep.MODE -> ModeStep(viewModel)
                OnboardingStep.SERVER -> ServerStep(viewModel)
                OnboardingStep.HOUSEHOLD -> HouseholdStep(state, viewModel)
                OnboardingStep.CREATE -> CreateStep(state, viewModel)
            }

            state.error?.let { ErrorBanner(it) }
        }
    }

    state.pinPromptFor?.let { target ->
        PinDialog(
            name = target.name,
            busy = state.busy,
            onSubmit = viewModel::submitPin,
            onDismiss = viewModel::dismissPin,
        )
    }
}

/** Branded header: the app-icon badge (terracotta + utensils glyph) over title + tagline. */
@Composable
private fun Hero() {
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            // The launcher foreground (utensils) carries the adaptive-icon safe-zone padding,
            // so filling the badge centres it nicely; tint to onPrimary for theme-proof contrast.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
            )
        }
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A step title — deliberately quieter than the Hero title so it reads as an
 *  instruction under the brand header, not a second competing title. */
@Composable
private fun StepHeader(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** First step: pick how to use openCook — offline-only on this phone, or with a home server. */
@Composable
private fun ModeStep(viewModel: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepHeader(stringResource(R.string.onboarding_mode_step_title))
        ChoiceCard(
            icon = Icons.Outlined.Smartphone,
            title = stringResource(R.string.onboarding_mode_local_title),
            subtitle = stringResource(R.string.onboarding_mode_local_subtitle),
            onClick = viewModel::useLocalOnly,
        )
        ChoiceCard(
            icon = Icons.Outlined.Dns,
            title = stringResource(R.string.onboarding_mode_server_title),
            subtitle = stringResource(R.string.onboarding_mode_server_subtitle),
            onClick = viewModel::chooseServerMode,
        )
        ChoiceCard(
            icon = Icons.Outlined.Devices,
            title = stringResource(R.string.onboarding_mode_serverless_title),
            subtitle = stringResource(R.string.onboarding_mode_serverless_subtitle),
            onClick = viewModel::chooseServerlessMode,
        )
    }
}

@Composable
private fun ServerStep(viewModel: OnboardingViewModel) {
    val servers by viewModel.discovered.collectAsStateWithLifecycle(initialValue = emptyList())
    var manual by remember { mutableStateOf("") }
    var manualExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepHeader(stringResource(R.string.onboarding_server_step_title))

        if (servers.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.onboarding_searching),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            servers.forEach { server ->
                // Peer phones advertise the same service with role=peer: joining through
                // one uses its endpoints directly and never stores it as "the server".
                val isPeer = server.role == DiscoveredRole.PEER
                ChoiceCard(
                    icon = if (isPeer) Icons.Outlined.Smartphone else Icons.Outlined.Dns,
                    title = server.name,
                    subtitle = if (isPeer) {
                        stringResource(R.string.onboarding_peer_subtitle)
                    } else {
                        "${server.host}:${server.port}"
                    },
                    onClick = {
                        if (isPeer) {
                            viewModel.choosePeer(server)
                        } else {
                            viewModel.chooseServer("${server.host}:${server.port}")
                        }
                    },
                )
            }
        }

        // Manual address is a fallback (auto-discovery covers the common case), so it stays
        // collapsed behind a toggle to keep the step uncluttered.
        TextButton(
            onClick = { manualExpanded = !manualExpanded },
            modifier = Modifier.padding(top = Spacing.xs),
        ) {
            Text(stringResource(R.string.onboarding_manual_toggle))
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                if (manualExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        AnimatedVisibility(visible = manualExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text(stringResource(R.string.onboarding_manual_address_label)) },
                    placeholder = { Text(stringResource(R.string.onboarding_manual_address_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.chooseServer(manual) },
                    enabled = manual.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_connect))
                }
            }
        }
    }
}

@Composable
private fun HouseholdStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepHeader(stringResource(R.string.onboarding_household_step_title))

        if (state.loadingHouseholds) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (state.households.isEmpty()) {
            Text(
                stringResource(R.string.onboarding_no_households),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Plain column (outer Column already scrolls) to avoid nested-scroll crashes.
            state.households.forEach { household ->
                HouseholdRow(household, enabled = !state.busy) { viewModel.selectHousehold(household) }
            }
        }

        FilledTonalButton(
            onClick = viewModel::goToCreate,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.onboarding_create_household))
        }
    }
}

@Composable
private fun HouseholdRow(household: HouseholdSummaryDto, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Outlined.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(household.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.onboarding_persons, household.settings.householdSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A lock for protected households, otherwise an affordance chevron.
            if (household.protected) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.onboarding_protected),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A tappable choice (discovered server) styled as an icon · text · chevron card. */
@Composable
private fun ChoiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    var name by remember { mutableStateOf("") }
    var size by remember { mutableIntStateOf(2) }
    var pin by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StepHeader(stringResource(R.string.onboarding_create_step_title))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.onboarding_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedButton(onClick = { if (size > 1) size-- }, enabled = size > 1) { Text("−") }
            Text(
                stringResource(R.string.onboarding_persons, size),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(onClick = { size++ }) { Text("+") }
        }
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text(stringResource(R.string.onboarding_pin_optional_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.createHousehold(name, size, pin) },
            enabled = !state.busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_create))
        }
        OutlinedButton(onClick = viewModel::back, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_back))
        }
    }
}

/** Inline error in a calm error-container surface, not raw red text. */
@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun PinDialog(name: String, busy: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_pin_prompt_title, name)) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text(stringResource(R.string.onboarding_pin_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = !busy && pin.isNotBlank()) {
                Text(stringResource(R.string.onboarding_join))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_cancel)) }
        },
    )
}
