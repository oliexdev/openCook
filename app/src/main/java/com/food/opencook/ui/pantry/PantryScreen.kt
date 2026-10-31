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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.food.opencook.R
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.ui.components.CategoryHeader
import com.food.opencook.ui.components.SwipeActionRow
import com.food.opencook.ui.components.SwipeConfirmEvent
import com.food.opencook.ui.components.SwipeConfirmLayer
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.GroceryCategories
import com.food.opencook.util.GroceryCategory
import com.food.opencook.repository.isBasic

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
    val swipeHintSeen by viewModel.swipeHintSeen.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Confirmation-glow overlay, hosted at list level so it outlives the removed row.
    var swipeEvent by remember { mutableStateOf<SwipeConfirmEvent?>(null) }
    var swipeTick by remember { mutableIntStateOf(0) }
    var listBoxOffset by remember { mutableStateOf(Offset.Zero) }
    val onSwipeConfirm: (Boolean, Rect) -> Unit = { deleting, bounds ->
        swipeTick += 1
        swipeEvent = SwipeConfirmEvent(bounds, deleting, swipeTick)
    }
    val deletedMsg = stringResource(R.string.deleted)
    val undoMsg = stringResource(R.string.undo)
    val addedToShoppingMsg = stringResource(R.string.shopping_added)
    // Scrolling the list dismisses the keyboard (e.g. while searching), so it doesn't cover results.
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { listBoxOffset = it.positionInRoot() }) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
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
            // Household-taught overrides beat the keyword heuristic.
            val overrides by viewModel.overrides.collectAsStateWithLifecycle()
            // "Basics (always in stock)" — what cooking never uses up (salt, flour, …; see
            // PantryItemEntity.basic) — is its own group at the end, collapsed, so the list
            // leads with the actual stock. The household moves items in and out by dragging,
            // like between aisles. Its items still cover the shopping list like any other
            // (GitHub #26), so the group opens on a tap, and on its own while searching or
            // when it is all there is.
            val (basics, stock) = visible.partition { it.isBasic() }
            var basicsOpen by rememberSaveable { mutableStateOf(false) }
            val basicsForced = !searchQuery.isNullOrBlank() || stock.isEmpty()
            val showBasics = basicsOpen || basicsForced
            // While an item is dragged every aisle is shown, so an empty one can be a target (#41).
            val dragging = remember { mutableStateOf(false) }
            val grouped = GroceryCategories.sections(
                stock.groupBy { GroceryCategories.categorize(it.name, overrides) },
                includeEmpty = dragging.value,
            )
            // The very first row peeks its swipe action once (one-time discoverability hint).
            val firstRowId = grouped.firstNotNullOfOrNull { it.second.firstOrNull() }?.id
                ?: basics.firstOrNull()?.id?.takeIf { showBasics }
            // Every list key (headers + rows) → the drop target its section is, for hit-testing.
            val keyTarget: Map<Any, PantryDrop> = buildMap {
                grouped.forEach { (category, list) ->
                    put("h_${category.name}", PantryDrop.Aisle(category))
                    list.forEach { put(it.id, PantryDrop.Aisle(category)) }
                }
                put(BASICS_KEY, PantryDrop.Basics)
                if (showBasics) basics.forEach { put(it.id, PantryDrop.Basics) }
            }
            val currentItems by rememberUpdatedState(items)

            // Drag-to-recategorize — identical mechanics to the shopping list (and the
            // meal planner): one target on the list, y-hit-testing, edge auto-scroll.
            // The lesson lands in the same shared override store, so both lists learn.
            val listBounds = remember { mutableStateOf(Rect.Zero) }
            val hovered = remember { mutableStateOf<PantryDrop?>(null) }
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
            val toBasicsTemplate = stringResource(R.string.pantry_moved_to_basics)
            val fromBasicsTemplate = stringResource(R.string.pantry_moved_from_basics)
            val categoryLabels = GroceryCategory.entries.associateWith { stringResource(it.labelRes) }
            // Read through updated state, not as remember keys: the aisles change the moment a
            // drag starts, and a new target object would cut the running drag session.
            val currentKeyTarget by rememberUpdatedState(keyTarget)
            val currentOverrides by rememberUpdatedState(overrides)
            val dropTarget = remember(listState) {
                fun targetAtY(y: Float): PantryDrop? {
                    val localY = y - listBounds.value.top
                    val rows = listState.layoutInfo.visibleItemsInfo
                    if (rows.isEmpty()) return null
                    val hit = rows.firstOrNull { localY >= it.offset && localY < it.offset + it.size }
                        ?: rows.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - localY) }
                    return hit?.key?.let { currentKeyTarget[it] }
                }
                object : DragAndDropTarget {
                    override fun onStarted(event: DragAndDropEvent) {
                        dragging.value = true
                    }
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
                        hovered.value = targetAtY(e.y)
                    }
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        scrollSpeed.floatValue = 0f
                        hovered.value = null
                        val e = event.toAndroidDragEvent()
                        val name = e.clipData?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString() ?: return false
                        val wasBasic = currentItems.firstOrNull { it.name == name }?.isBasic() == true
                        val message = when (val target = targetAtY(e.y) ?: return false) {
                            PantryDrop.Basics -> {
                                if (wasBasic) return false
                                viewModel.setBasic(name, true)
                                toBasicsTemplate.format(name)
                            }
                            is PantryDrop.Aisle -> {
                                // Out of the basics onto an aisle: back to regular
                                // stock — and, if it's another aisle, learned like any move.
                                val moved = target.category != GroceryCategories.categorize(name, currentOverrides)
                                if (!wasBasic && !moved) return false
                                if (wasBasic) viewModel.setBasic(name, false)
                                if (moved) viewModel.recategorize(name, target.category)
                                if (moved) learnedTemplate.format(name, categoryLabels[target.category] ?: "")
                                else fromBasicsTemplate.format(name)
                            }
                        }
                        scope.launch { snackbar.showSnackbar(message) }
                        return true
                    }
                    override fun onExited(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hovered.value = null
                    }
                    override fun onEnded(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hovered.value = null; dragging.value = false
                    }
                }
            }

            val pantryRow: @Composable LazyItemScope.(PantryItemEntity) -> Unit = { item ->
                PantryRow(
                    name = item.name,
                    onConfirm = onSwipeConfirm,
                    modifier = Modifier.animateItem(),
                    peek = !swipeHintSeen && item.id == firstRowId,
                    onPeekShown = { viewModel.markSwipeHintSeen() },
                    // Swipe: plain, no snackbar (overlay is the feedback).
                    onSwipeAddToShopping = { viewModel.addToShopping(item.name) },
                    onSwipeDelete = { viewModel.delete(item.id) },
                    // Menu: keep snackbar feedback / undo.
                    onAddToShopping = {
                        viewModel.addToShopping(item.name)
                        scope.launch { snackbar.showSnackbar(addedToShoppingMsg) }
                    },
                    onDelete = {
                        viewModel.delete(item.id)
                        scope.launch {
                            if (snackbar.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                viewModel.restore(item)
                            }
                        }
                    },
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
                            empty = list.isEmpty(),
                            modifier = Modifier.fillMaxWidth().background(
                                if (hovered.value == PantryDrop.Aisle(category)) {
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
                    items(list, key = { it.id }) { item -> pantryRow(item) }
                }
                // Always there, even empty: the header is also where items are dragged to.
                item(key = BASICS_KEY) {
                    BasicsHeader(
                        count = basics.size,
                        preview = basics.joinToString(", ") { it.name },
                        expanded = showBasics,
                        onToggle = if (basicsForced || basics.isEmpty()) null else ({ basicsOpen = !basicsOpen }),
                        highlighted = hovered.value == PantryDrop.Basics,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (showBasics) items(basics, key = { it.id }) { item -> pantryRow(item) }
            }
            }
        }
    }
        SwipeConfirmLayer(
            event = swipeEvent,
            boxOffsetInRoot = listBoxOffset,
            addLabel = stringResource(R.string.swipe_to_shopping),
        )
    }
}

/** A pantry drag's drop target: an aisle (recategorize), or the basics group. */
private sealed interface PantryDrop {
    data class Aisle(val category: GroceryCategory) : PantryDrop
    data object Basics : PantryDrop
}

private const val BASICS_KEY = "h_basics"

/**
 * Header of the basics group (see [PantryBody]): its name and size, collapsed
 * with a one-line preview of what's in it — or, while empty, a hint to drag items here.
 * [onToggle] is null while there is nothing to toggle (held open, or empty).
 */
@Composable
private fun BasicsHeader(
    count: Int,
    preview: String,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(if (expanded) R.string.pantry_basics_hide else R.string.pantry_basics_show)
    Column(
        modifier
            .fillMaxWidth()
            // Same transient drag feedback as an aisle header.
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.background.copy(alpha = 0f),
                RoundedCornerShape(8.dp),
            )
            .then(
                if (onToggle == null) Modifier
                else Modifier.clickable(onClickLabel = state, onClick = onToggle)
                    .semantics { stateDescription = state },
            )
            .padding(top = Spacing.md, bottom = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🏠  ${stringResource(R.string.pantry_basics)} · $count",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (onToggle != null) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (count == 0 || !expanded) {
            Text(
                if (count == 0) stringResource(R.string.pantry_basics_empty) else preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Block-based dragAndDropSource is deprecated but is the only variant that triggers on a
// real long-press (the plain-drag overloads fight the LazyColumn scroll) — same reasoning
// as the meal planner's PlannedRow and the shopping list's ShoppingRow.
@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PantryRow(
    name: String,
    // Menu callbacks keep their snackbar (no overlay there); swipe callbacks are plain
    // (the confirmation overlay is the feedback, no snackbar on swipe).
    onAddToShopping: () -> Unit,
    onDelete: () -> Unit,
    onSwipeAddToShopping: () -> Unit,
    onSwipeDelete: () -> Unit,
    onConfirm: (deleting: Boolean, boundsInRoot: Rect) -> Unit,
    modifier: Modifier = Modifier,
    peek: Boolean = false,
    onPeekShown: () -> Unit = {},
) {
    // Captured here because the drag-shadow lambda below runs in DrawScope (no theme access).
    val shadowColor = MaterialTheme.colorScheme.surfaceContainerHighest
    // Left swipe = delete, right swipe = onto the shopping list (see SwipeActionRow). The full
    // action list also lives in the row's ⋮ menu, so swipe is only an accelerator.
    // actionRemovesRow = false: the pantry keeps the item (it's still in stock), so the right
    // swipe snaps back rather than sliding off.
    SwipeActionRow(
        onDelete = onSwipeDelete,
        onAction = onSwipeAddToShopping,
        actionIcon = Icons.Outlined.AddShoppingCart,
        actionLabel = stringResource(R.string.shopping_add_from_recipe),
        onConfirm = onConfirm,
        modifier = modifier.padding(vertical = Spacing.xs),
        actionRemovesRow = false,
        peek = peek,
        onPeekShown = onPeekShown,
    ) {
        // Same card chrome as a shopping-list row, so the two views match.
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    // Long-press lifts the line as a drag source — dropping it on another
                    // aisle teaches the categorization.
                    .dragAndDropSource(
                        drawDragDecoration = {
                            drawRoundRect(color = shadowColor, cornerRadius = CornerRadius(12.dp.toPx()))
                        },
                        block = {
                            detectTapGestures(
                                onLongPress = {
                                    startTransfer(
                                        DragAndDropTransferData(ClipData.newPlainText("grocery", name)),
                                    )
                                },
                            )
                        },
                    )
                    .padding(vertical = 2.dp, horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                )
                // Single overflow with the full action list; swipe accelerates the same two.
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.shopping_actions))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_add_from_recipe)) },
                            leadingIcon = { Icon(Icons.Outlined.AddShoppingCart, contentDescription = null) },
                            onClick = { menuOpen = false; onAddToShopping() },
                        )
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
