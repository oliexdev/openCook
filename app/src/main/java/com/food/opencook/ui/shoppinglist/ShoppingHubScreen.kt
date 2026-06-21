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

package com.food.opencook.ui.shoppinglist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.AutocompleteAddField
import com.food.opencook.ui.components.LockPortraitOnCompact
import com.food.opencook.ui.pantry.PantryBody
import com.food.opencook.ui.pantry.PantryViewModel
import com.food.opencook.ui.theme.Spacing

private const val SEGMENT_SHOPPING = 0
private const val SEGMENT_PANTRY = 1

private enum class BarMode { NORMAL, SEARCH, ADD }

/**
 * The "Einkauf" tab: a hub that switches between the shopping list and the pantry — the two
 * halves of one kitchen inventory ("what I need" ↔ "what I have"). They share one top bar
 * (with the sync indicator), one snackbar, and one bar that morphs into a live search or an
 * add field on demand; a segmented control flips between the two lists. The pantry used to
 * be buried behind an icon here; promoting it to a peer segment makes it discoverable
 * without spending a sixth bottom-nav slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingHubScreen(
    onScanBarcodeShopping: () -> Unit = {},
    onScanBarcodePantry: () -> Unit = {},
    shoppingVm: ShoppingListViewModel = hiltViewModel(),
    pantryVm: PantryViewModel = hiltViewModel(),
) {
    // One-handed at the supermarket / in the kitchen: pin phones to portrait for both the
    // shopping list and the pantry segment. No-op on tablets (keeps their landscape layout).
    LockPortraitOnCompact()
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Survives config changes and the barcode-scan round-trip, so a pantry scan returns
    // to the pantry segment rather than snapping back to the shopping list.
    var segment by rememberSaveable { mutableStateOf(SEGMENT_SHOPPING) }
    val isPantry = segment == SEGMENT_PANTRY

    // The bar morphs to search/add on demand; both apply to whichever segment is active.
    var barMode by rememberSaveable { mutableStateOf(BarMode.NORMAL) }
    var query by rememberSaveable { mutableStateOf("") }
    var newItem by rememberSaveable { mutableStateOf("") }
    fun closeBar() { keyboard?.hide(); barMode = BarMode.NORMAL; query = ""; newItem = "" }

    // System back leaves search/add mode first (rather than leaving the tab).
    BackHandler(enabled = barMode != BarMode.NORMAL) { closeBar() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            // The search and add icons each turn the whole top bar into a field (one shared
            // pattern); the back arrow returns to the normal bar.
            when (barMode) {
                BarMode.SEARCH -> SearchTopBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClose = { closeBar() },
                )
                BarMode.ADD -> AddTopBar(
                    value = newItem,
                    onValueChange = { newItem = it },
                    suggestions = if (isPantry) pantryVm.suggestions(newItem) else shoppingVm.suggestions(newItem),
                    placeholder = stringResource(if (isPantry) R.string.pantry_add_hint else R.string.shopping_add_hint),
                    onAdd = {
                        if (newItem.isNotBlank()) {
                            if (isPantry) pantryVm.add(newItem) else shoppingVm.add(newItem)
                            newItem = "" // keep add mode open for the next item
                        }
                    },
                    onScan = if (isPantry) onScanBarcodePantry else onScanBarcodeShopping,
                    onClose = { closeBar() },
                )
                BarMode.NORMAL -> AppTopBar(
                    title = stringResource(if (isPantry) R.string.segment_pantry else R.string.segment_shopping),
                    syncStatus = syncStatus,
                    onSync = appBar::sync,
                    actions = {
                        // Search + add both sit right next to the sync icon, for both segments.
                        IconButton(onClick = { query = ""; barMode = BarMode.SEARCH }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search))
                        }
                        IconButton(onClick = { newItem = ""; barMode = BarMode.ADD }) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.shopping_add))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SingleChoiceSegmentedButtonRow(
                Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            ) {
                val labels = listOf(
                    stringResource(R.string.segment_shopping),
                    stringResource(R.string.segment_pantry),
                )
                labels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = segment == index,
                        onClick = { segment = index; query = ""; newItem = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    ) { Text(label) }
                }
            }

            val searchQuery = if (barMode == BarMode.SEARCH) query else null
            when (segment) {
                SEGMENT_PANTRY -> PantryBody(viewModel = pantryVm, snackbar = snackbar, searchQuery = searchQuery)
                else -> ShoppingListBody(viewModel = shoppingVm, snackbar = snackbar, searchQuery = searchQuery)
            }
        }
    }
}

/**
 * The top bar in search mode: a back arrow that exits search, an auto-focused borderless
 * search field filling the bar, and a trailing X that clears the typed text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.search_close))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                colors = transparentFieldColors(),
                // Live search needs no submit — Enter just dismisses the keyboard.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        colors = barColors(),
    )
}

/**
 * The top bar in add mode: a back arrow that exits, plus the auto-focused autocomplete add
 * field (with barcode scan + add button). The field stays open after each add, so several
 * items can be typed in a row; back closes the mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTopBar(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    placeholder: String,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        title = {
            AutocompleteAddField(
                value = value,
                onValueChange = onValueChange,
                suggestions = suggestions,
                onAdd = onAdd,
                placeholder = placeholder,
                addLabel = stringResource(R.string.shopping_add),
                modifier = Modifier.fillMaxWidth(),
                scanContentDescription = stringResource(R.string.barcode_scan_cd),
                onScan = onScan,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)
