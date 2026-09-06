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

package com.food.opencook.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.food.opencook.R
import com.food.opencook.ui.LocalSnackbarHostState
import com.food.opencook.ui.components.AutocompleteAddField
import com.food.opencook.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Uniform tile height so a two-line site name never makes a row jump. */
private val TILE_HEIGHT = 96.dp

/** The corner ✕: small enough not to compete with the tile, big enough to hit. */
private val REMOVE_TOUCH = 36.dp
private val REMOVE_ICON = 16.dp

/**
 * Starting points for browsing recipes on the web. Tapping a tile opens that site in the in-app
 * browser ([WebImportScreen]), where the site's own listing does the browsing and a button in the
 * bar imports the recipe that is open.
 *
 * The board belongs to the user: the ✕ on a tile takes it off (undoable), and the "+" in the
 * bar adds any address — the same add-bar the shopping list and pantry use.
 *
 * openCook reads nothing here on its own: no listing pages, no prefetching. The tiles carry
 * names, not logos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val removedDefaults by viewModel.removedDefaults.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var adding by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    fun closeAdd() {
        keyboard?.hide()
        adding = false
        typed = ""
    }

    // Which sites have put something on this phone. Re-read every time the board appears, so a
    // visit that just accepted a cookie banner shows its broom on the way back.
    var storedFor by remember { mutableStateOf(emptySet<String>()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(sites, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            storedFor = sites.map { it.url }.filter(BrowserData::hasDataFor).toSet()
        }
    }

    // Asked before wiping: the site forgets the consent you gave it, and unlike removing a tile
    // this cannot be undone — there is no putting a cookie back.
    val clearedMessage = stringResource(R.string.discover_cleared)
    var confirmClear by remember { mutableStateOf<DiscoverSite?>(null) }
    confirmClear?.let { site ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(stringResource(R.string.discover_clear_confirm_title, site.label)) },
            text = { Text(stringResource(R.string.discover_clear_confirm_text, site.label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = null
                        BrowserData.clearSite(site.url)
                        storedFor = storedFor - site.url
                        scope.launch {
                            snackbarHostState.showSnackbar(clearedMessage.format(site.label))
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = null }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    val removedMessage = stringResource(R.string.discover_removed)
    val undoLabel = stringResource(R.string.undo)
    val onRemove: (DiscoverSite) -> Unit = { site ->
        viewModel.remove(site.url)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = removedMessage.format(site.label),
                actionLabel = undoLabel,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.add(site.url)
        }
    }

    Scaffold(
        topBar = {
            if (adding) {
                AddAddressBar(
                    value = typed,
                    onValueChange = { typed = it },
                    suggestions = removedDefaults,
                    onAdd = {
                        val url = DiscoverSites.normalizeUrl(typed)
                        if (url != null) {
                            viewModel.add(url)
                            closeAdd()
                            onOpen(url)
                        }
                    },
                    onClose = { closeAdd() },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.discover_title)) },
                    colors = barColors(),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { adding = true }) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(R.string.discover_add),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
        ) {
            Column(
                Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                sites.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        pair.forEach { site ->
                            SiteTile(
                                site = site,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpen(site.url) },
                                onRemove = { onRemove(site) },
                                onClear = { confirmClear = site }.takeIf { site.url in storedFor },
                            )
                        }
                        // Keeps a lone last tile at column width instead of stretching it.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text(
                stringResource(R.string.discover_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            )
        }
    }
}

@Composable
private fun SiteTile(
    site: DiscoverSite,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    OutlinedCard(onClick = onClick, modifier = modifier.height(TILE_HEIGHT)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    Icons.Outlined.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    site.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    site.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Opposite the globe, so the corner reads as "act on this one" and not as decoration.
            // Quiet on purpose: the board is for opening sites, these two are the rare acts.
            Row(Modifier.align(Alignment.TopEnd)) {
                // Only shown once the site has actually stored something — the broom appearing is
                // itself the message "this site remembers you now".
                if (onClear != null) {
                    IconButton(onClick = onClear, modifier = Modifier.size(REMOVE_TOUCH)) {
                        Icon(
                            Icons.Outlined.CleaningServices,
                            contentDescription = stringResource(R.string.discover_clear, site.label),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(REMOVE_ICON),
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(REMOVE_TOUCH)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.discover_remove, site.label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(REMOVE_ICON),
                    )
                }
            }
        }
    }
}

/** The shopping list's add-bar, reused: the "+" turns the title bar into one input line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAddressBar(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        title = {
            AutocompleteAddField(
                value = value,
                onValueChange = onValueChange,
                suggestions = suggestions,
                onAdd = onAdd,
                placeholder = stringResource(R.string.discover_address_hint),
                addLabel = stringResource(R.string.discover_add),
                modifier = Modifier.fillMaxWidth(),
                autoFocus = true,
            )
        },
        colors = barColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun barColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background,
)
