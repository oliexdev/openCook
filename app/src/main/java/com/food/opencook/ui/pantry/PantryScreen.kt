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

package com.food.opencook.ui.pantry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.food.opencook.R
import com.food.opencook.ui.components.CategoryHeader
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.GroceryCategories

/**
 * The pantry view, hosted as one segment of the "Einkauf" tab (see ShoppingHubScreen).
 * Content only — the top bar and sync indicator live in the hub. Items are name-only and
 * grouped by store aisle into the same card rows as the shopping list, so the two read as
 * one inventory.
 */
@Composable
fun PantryBody(
    viewModel: PantryViewModel,
    snackbar: SnackbarHostState,
    searchQuery: String? = null,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val deletedMsg = stringResource(R.string.deleted)
    val undoMsg = stringResource(R.string.undo)
    // Scrolling the list dismisses the keyboard (e.g. while searching), so it doesn't cover results.
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val visible = if (searchQuery.isNullOrBlank()) items
            else items.filter { it.name.contains(searchQuery, ignoreCase = true) }

        when {
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pantry_empty), style = MaterialTheme.typography.bodyMedium)
            }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
            // Group by store aisle like the shopping list. Items are name-only, so the
            // source's alphabetical order is preserved within each group.
            val grouped = visible.groupBy { GroceryCategories.categorize(it.name) }
                .toList().sortedBy { it.first.ordinal }

            LazyColumn(state = listState, modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                grouped.forEach { (category, list) ->
                    item(key = "h_${category.name}") { CategoryHeader(category) }
                    items(list, key = { it.id }) { item ->
                        // Same card chrome as a shopping-list row, so the two views match.
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                                )
                                IconButton(onClick = {
                                    viewModel.delete(item.id)
                                    scope.launch {
                                        if (snackbar.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                                            viewModel.restore(item)
                                        }
                                    }
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.shopping_delete))
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
