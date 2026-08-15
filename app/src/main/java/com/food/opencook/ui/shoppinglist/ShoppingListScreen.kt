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

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.ui.components.CategoryHeader
import com.food.opencook.ui.components.ConfettiOverlay
import com.food.opencook.ui.components.KeepScreenOn
import com.food.opencook.ui.components.SwipeActionRow
import com.food.opencook.ui.components.SwipeConfirmEvent
import com.food.opencook.ui.components.SwipeConfirmLayer
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.GroceryCategories
import com.food.opencook.util.GroceryCategory
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
    val skipped by viewModel.skippedItems.collectAsStateWithLifecycle()
    val allChecked by viewModel.allChecked.collectAsStateWithLifecycle()
    val swipeHintSeen by viewModel.swipeHintSeen.collectAsStateWithLifecycle()
    // Confirmation-glow overlay, hosted at list level so it outlives the removed row.
    var swipeEvent by remember { mutableStateOf<SwipeConfirmEvent?>(null) }
    var swipeTick by remember { mutableIntStateOf(0) }
    var listBoxOffset by remember { mutableStateOf(Offset.Zero) }
    val onSwipeConfirm: (Boolean, Rect) -> Unit = { deleting, bounds ->
        swipeTick += 1
        swipeEvent = SwipeConfirmEvent(bounds, deleting, swipeTick)
    }

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
            if (snackbar.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) viewModel.restore(item)
        }
    }
    val movedToPantryMsg = stringResource(R.string.shopping_moved_to_pantry)
    val alreadyHomeWithHint: (ShoppingItemEntity) -> Unit = { item ->
        viewModel.markAlreadyAtHome(item)
        scope.launch { snackbar.showSnackbar(movedToPantryMsg) }
    }
    val adaptedMsg = stringResource(R.string.shopping_adapted)
    val adaptWithUndo: (ShoppingItemEntity) -> Unit = { item ->
        viewModel.setChecked(item.id, true)
        scope.launch {
            if (snackbar.showSnackbar(adaptedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                viewModel.setChecked(item.id, false)
            }
        }
    }

    // Cap the content width on tablets/landscape and centre it, so the item name and
    // its action menu stay close together — a full-width row pushed the menu to the far
    // edge and made it easy to delete the wrong line. On phones this is a no-op.
    Box(modifier.fillMaxSize().onGloballyPositioned { listBoxOffset = it.positionInRoot() }) {
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
            // Household-taught overrides beat the keyword heuristic.
            val overrides by viewModel.overrides.collectAsStateWithLifecycle()
            val grouped = visible.groupBy { GroceryCategories.categorize(it.item.text, overrides) }
                .toList().sortedBy { it.first.ordinal }
            // The very first row peeks its swipe action once (one-time discoverability hint).
            val firstRowId = grouped.firstOrNull()?.second?.firstOrNull()?.item?.id
            // Every list key (headers + rows) → its section's category, for drop hit-testing.
            val keyCategory = buildMap {
                grouped.forEach { (category, list) ->
                    put("h_${category.name}", category)
                    list.forEach { put(it.item.id, category) }
                }
            }

            // Drag-to-recategorize: dropping a dish on another aisle group teaches the
            // household "this name belongs there" (GroceryOverrideRepository) — the item
            // itself never changes; the list re-groups reactively. Same single-target +
            // edge-auto-scroll mechanics as the meal planner's drag-to-reschedule.
            val listBounds = remember { mutableStateOf(Rect.Zero) }
            val hoveredCategory = remember { mutableStateOf<GroceryCategory?>(null) }
            val scrollSpeed = remember { mutableFloatStateOf(0f) }
            val edgeZonePx = with(LocalDensity.current) { 72.dp.toPx() }
            val maxStepPx = with(LocalDensity.current) { 18.dp.toPx() }
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { }
                    val v = scrollSpeed.floatValue
                    if (v != 0f) listState.scrollBy(v)
                }
            }
            val learnedTemplate = stringResource(R.string.shopping_category_learned)
            val categoryLabels = GroceryCategory.entries.associateWith { stringResource(it.labelRes) }
            val dropTarget = remember(listState, keyCategory) {
                fun categoryAtY(y: Float): GroceryCategory? {
                    val localY = y - listBounds.value.top
                    val items = listState.layoutInfo.visibleItemsInfo
                    if (items.isEmpty()) return null
                    val hit = items.firstOrNull { localY >= it.offset && localY < it.offset + it.size }
                        ?: items.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - localY) }
                    return hit?.key?.let { keyCategory[it] }
                }
                object : DragAndDropTarget {
                    override fun onMoved(event: DragAndDropEvent) {
                        val e = event.toAndroidDragEvent()
                        val b = listBounds.value
                        scrollSpeed.floatValue = when {
                            e.y < b.top + edgeZonePx ->
                                -maxStepPx * ((b.top + edgeZonePx - e.y) / edgeZonePx).coerceIn(0f, 1f)
                            e.y > b.bottom - edgeZonePx ->
                                maxStepPx * ((e.y - (b.bottom - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                            else -> 0f
                        }
                        hoveredCategory.value = categoryAtY(e.y)
                    }
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        scrollSpeed.floatValue = 0f
                        hoveredCategory.value = null
                        val e = event.toAndroidDragEvent()
                        val name = e.clipData?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString() ?: return false
                        val target = categoryAtY(e.y) ?: return false
                        if (target == GroceryCategories.categorize(name, overrides)) return false
                        viewModel.recategorize(name, target)
                        scope.launch {
                            snackbar.showSnackbar(learnedTemplate.format(name, categoryLabels[target] ?: ""))
                        }
                        return true
                    }
                    override fun onExited(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hoveredCategory.value = null
                    }
                    override fun onEnded(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hoveredCategory.value = null
                    }
                }
            }

            // Recipe items the pantry (apparently) covered, hidden from the list but shown
            // here so a wrong skip is visible and recoverable. Staples are never listed.
            if (searchQuery == null && skipped.isNotEmpty()) {
                SkippedChip(
                    skipped = skipped,
                    onKeep = { viewModel.keepAnyway(it) },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()
                    .onGloballyPositioned { listBounds.value = it.boundsInRoot() }
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                        target = dropTarget,
                    ),
            ) {
                grouped.forEach { (category, list) ->
                    item(key = "h_${category.name}") {
                        CategoryHeader(
                            category,
                            modifier = Modifier.fillMaxWidth().background(
                                if (hoveredCategory.value == category) {
                                    // Transient drag feedback: the app pointing at a target,
                                    // not a state of the food. Green is reserved for the latter.
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                },
                                RoundedCornerShape(8.dp),
                            ),
                        )
                    }
                    items(list, key = { it.item.id }) { row ->
                        val item = row.item
                        ShoppingRow(
                            row = row,
                            onToggle = { viewModel.setChecked(item.id, it) },
                            onAlreadyHome = { alreadyHomeWithHint(item) },
                            onMarkDone = { adaptWithUndo(item) },
                            onDelete = { deleteWithUndo(item) },
                            onSwipeAlreadyHome = { viewModel.markAlreadyAtHome(item) },
                            onSwipeDelete = { viewModel.delete(item.id) },
                            onConfirm = onSwipeConfirm,
                            modifier = Modifier.animateItem(),
                            peek = !swipeHintSeen && item.id == firstRowId,
                            onPeekShown = { viewModel.markSwipeHintSeen() },
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
        SwipeConfirmLayer(
            event = swipeEvent,
            boxOffsetInRoot = listBoxOffset,
            addLabel = stringResource(R.string.swipe_to_pantry),
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
            // A second line is still allowed — at large text sizes it no longer fits on one.
            Button(
                onClick = onCheckout,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            ) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    stringResource(R.string.shopping_all_bought_action),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Collapsible "N im Vorrat übersprungen" chip: recipe items the pantry covered and thus hid
 * from the list. Tapping "brauch ich doch" teaches the household that this item and the
 * covering pantry stock are different products, so the item returns to the list.
 */
@Composable
private fun SkippedChip(
    skipped: List<SkippedItemUi>,
    onKeep: (SkippedItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.padding(vertical = Spacing.xs),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    stringResource(R.string.shopping_skipped_chip, skipped.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (expanded) {
                skipped.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onKeep(s) }) {
                            Text(stringResource(R.string.shopping_need_anyway))
                        }
                    }
                }
            }
        }
    }
}

// Block-based dragAndDropSource is deprecated but is the only variant that triggers on a
// real long-press (the plain-drag overloads fight the LazyColumn scroll) — same reasoning
// as the meal planner's PlannedRow.
@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingRow(
    row: ShoppingRowUi,
    onToggle: (Boolean) -> Unit,
    // Menu callbacks keep their snackbar/undo (no overlay there); swipe callbacks are plain
    // (the confirmation overlay is the feedback, no snackbar on swipe).
    onAlreadyHome: () -> Unit,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit,
    onSwipeAlreadyHome: () -> Unit,
    onSwipeDelete: () -> Unit,
    onConfirm: (deleting: Boolean, boundsInRoot: Rect) -> Unit,
    modifier: Modifier = Modifier,
    peek: Boolean = false,
    onPeekShown: () -> Unit = {},
) {
    val item = row.item
    var menuOpen by remember { mutableStateOf(false) }
    // Captured here because the drag-shadow lambda below runs in DrawScope (no theme access).
    val shadowColor = MaterialTheme.colorScheme.surfaceContainerHighest
    // Swipe is an accelerator: left = delete, right = "already at home" (→ pantry). The
    // visible icon buttons keep both actions discoverable; the ⋮ holds the rare "not found".
    SwipeActionRow(
        onDelete = onSwipeDelete,
        onAction = onSwipeAlreadyHome,
        actionIcon = Icons.Outlined.Home,
        actionLabel = stringResource(R.string.shopping_already_home),
        onConfirm = onConfirm,
        modifier = modifier.padding(vertical = Spacing.xs),
        peek = peek,
        onPeekShown = onPeekShown,
    ) {
        // Each line is its own card so it reads as one bounded tap target — important on
        // wide screens where name and actions would otherwise sit far apart.
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    // ONE detector for both gestures (a separate .clickable would swallow the
                    // long-press): tap toggles the checkmark, long-press lifts the line as a
                    // drag source — dropping it on another aisle teaches the categorization.
                    .dragAndDropSource(
                        drawDragDecoration = {
                            drawRoundRect(color = shadowColor, cornerRadius = CornerRadius(12.dp.toPx()))
                        },
                        block = {
                            detectTapGestures(
                                onTap = { onToggle(!item.checked) },
                                onLongPress = {
                                    startTransfer(
                                        DragAndDropTransferData(ClipData.newPlainText("grocery", item.text)),
                                    )
                                },
                            )
                        },
                    )
                    .padding(vertical = 2.dp, horizontal = Spacing.xs),
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
                // Single overflow with the full action list; swipe accelerates delete + "→ pantry".
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.shopping_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!item.checked) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_already_home)) },
                                leadingIcon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                                onClick = { menuOpen = false; onAlreadyHome() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_nf_adapt)) },
                                leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                onClick = { menuOpen = false; onMarkDone() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}
