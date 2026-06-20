package com.food.opencook.ui.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.ui.components.CategoryHeader
import com.food.opencook.ui.components.ConfettiOverlay
import com.food.opencook.ui.components.KeepScreenOn
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.GroceryCategories
import com.food.opencook.util.Numbers

/**
 * The shopping-list view, hosted as one segment of the "Einkauf" tab (see ShoppingHubScreen).
 * Content only — the top bar, sync indicator and bulk-action overflow live in the hub.
 */
@Composable
fun ShoppingListBody(
    viewModel: ShoppingListViewModel,
    snackbar: SnackbarHostState,
    searchQuery: String? = null,
    modifier: Modifier = Modifier,
) {
    // Keep the screen awake while the shopping list is open — you're using it on the move.
    // (Portrait-locking on phones is handled once at the hub level, covering both segments.)
    KeepScreenOn()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val allChecked by viewModel.allChecked.collectAsStateWithLifecycle()
    val notFound by viewModel.notFound.collectAsStateWithLifecycle()

    // Confetti only on a genuine finish: arm once there are open items, fire when they all
    // become checked. Re-opening an already-complete list never arms (no open items were
    // ever seen), so it stays quiet; checking-out empties the list and disarms.
    val hasOpenItems by remember { derivedStateOf { items.any { !it.item.checked } } }
    var armed by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(hasOpenItems, allChecked) {
        if (hasOpenItems) armed = true
        if (allChecked && armed) { showConfetti = true; armed = false }
    }
    val scope = rememberCoroutineScope()
    // Scrolling the list dismisses the keyboard (e.g. while searching), so it doesn't cover results.
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }
    val deletedMsg = stringResource(R.string.deleted)
    val undoMsg = stringResource(R.string.undo)
    val deleteWithUndo: (ShoppingItemEntity) -> Unit = { item ->
        viewModel.delete(item.id)
        scope.launch {
            if (snackbar.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.restore(item)
        }
    }
    val adaptedMsg = stringResource(R.string.shopping_adapted)
    val adaptWithUndo: (ShoppingItemEntity) -> Unit = { item ->
        viewModel.adaptItem(item.id)
        scope.launch {
            if (snackbar.showSnackbar(adaptedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.setChecked(item.id, false)
            }
        }
    }

    // Cap the content width on tablets/landscape and centre it, so the item name and
    // its action menu stay close together — a full-width row pushed the menu to the far
    // edge and made it easy to delete the wrong line. On phones this is a no-op.
    Box(modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val visible = if (searchQuery.isNullOrBlank()) items
            else items.filter { it.item.text.contains(searchQuery, ignoreCase = true) }

        when {
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.shopping_empty), style = MaterialTheme.typography.bodyMedium)
            }
            visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_no_results), style = MaterialTheme.typography.bodyMedium)
            }
            else -> {
            // Everything bought → a clear "done" banner whose action moves the bought
            // items into the pantry and clears the list (the "in" half of the cycle).
            if (searchQuery == null && allChecked) {
                AllBoughtBanner(
                    onCheckout = { viewModel.clearChecked() },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
            }
            // One flat list, grouped by store aisle. Checked items stay in place,
            // struck through — never auto-removed (use the bulk action to clear).
            val grouped = visible.groupBy { GroceryCategories.categorize(it.item.text) }
                .toList().sortedBy { it.first.ordinal }

            LazyColumn(state = listState, modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                grouped.forEach { (category, list) ->
                    item(key = "h_${category.name}") { CategoryHeader(category) }
                    items(list, key = { it.item.id }) { row ->
                        val item = row.item
                        ShoppingRow(
                            row = row,
                            onToggle = { viewModel.setChecked(item.id, it) },
                            onAlreadyHome = { viewModel.markAlreadyAtHome(item) },
                            onNotFound = { viewModel.openNotFound(item) },
                            onDelete = { deleteWithUndo(item) },
                        )
                    }
                }
            }
            }
        }
    }
        // Drawn above the list (and not intercepting taps) so the burst rains over the whole
        // screen the moment the last item is checked off.
        ConfettiOverlay(visible = showConfetti, onFinished = { showConfetti = false })
    }

    notFound?.let { state ->
        NotFoundDialog(
            state = state,
            onLater = viewModel::dismissNotFound,
            onReplace = viewModel::findAlternatives,
            onAdapt = { adaptWithUndo(state.item) },
            onPick = viewModel::replaceWith,
            onDismiss = viewModel::dismissNotFound,
        )
    }
}

@Composable
private fun AllBoughtBanner(onCheckout: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.padding(bottom = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(
                stringResource(R.string.shopping_all_bought_title),
                style = MaterialTheme.typography.titleMedium,
            )
            // Full-width CTA: the long German label ("In den Vorrat übernehmen") wrapped to two
            // lines inside a wrap-content button on narrow phones; stretching it edge-to-edge
            // gives the text room to stay on one line and reads as the clear primary action.
            Button(
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            ) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.shopping_all_bought_action), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShoppingRow(
    row: ShoppingRowUi,
    onToggle: (Boolean) -> Unit,
    onAlreadyHome: () -> Unit,
    onNotFound: () -> Unit,
    onDelete: () -> Unit,
) {
    val item = row.item
    var menuOpen by remember { mutableStateOf(false) }
    // Each line is its own card so it reads as one bounded tap target — important on
    // wide screens where name and action menu would otherwise sit far apart.
    Card(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!item.checked) }.padding(vertical = 2.dp, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(
                text = Numbers.displayIngredient(item.quantity, item.unit, item.text),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            if (row.recipeNames.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_needed_for, row.recipeNames.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.shopping_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!item.checked) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_already_home)) },
                        onClick = { menuOpen = false; onAlreadyHome() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.shopping_not_found)) },
                        onClick = { menuOpen = false; onNotFound() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shopping_delete)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
    }
}

@Composable
private fun NotFoundDialog(
    state: NotFoundUi,
    onLater: () -> Unit,
    onReplace: () -> Unit,
    onAdapt: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_nf_title, state.item.text)) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.processing_cancel)) }
        },
        text = {
            if (!state.searched) {
                Column {
                    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.shopping_nf_later))
                    }
                    if (state.canReplace) {
                        TextButton(onClick = onReplace, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.shopping_nf_replace))
                        }
                    } else {
                        Text(
                            stringResource(R.string.shopping_nf_manual),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    TextButton(onClick = onAdapt, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.shopping_nf_adapt))
                    }
                }
            } else if (state.suggestions.isEmpty()) {
                Text(stringResource(R.string.shopping_nf_no_alt))
            } else {
                Column {
                    Text(stringResource(R.string.shopping_nf_pick), style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(state.suggestions, key = { it.id }) { suggestion ->
                            Text(
                                suggestion.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable { onPick(suggestion.id) }.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}
