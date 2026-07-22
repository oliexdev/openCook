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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.food.opencook.R
import com.food.opencook.ui.components.CategoryHeader
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.GroceryCategories
import com.food.opencook.util.GroceryCategory

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
            // Household-taught overrides beat the keyword heuristic.
            val overrides by viewModel.overrides.collectAsStateWithLifecycle()
            val grouped = visible.groupBy { GroceryCategories.categorize(it.name, overrides) }
                .toList().sortedBy { it.first.ordinal }
            // Every list key (headers + rows) → its section's category, for drop hit-testing.
            val keyCategory = buildMap {
                grouped.forEach { (category, list) ->
                    put("h_${category.name}", category)
                    list.forEach { put(it.id, category) }
                }
            }

            // Drag-to-recategorize — identical mechanics to the shopping list (and the
            // meal planner): one target on the list, y-hit-testing, edge auto-scroll.
            // The lesson lands in the same shared override store, so both lists learn.
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
                    val rows = listState.layoutInfo.visibleItemsInfo
                    if (rows.isEmpty()) return null
                    val hit = rows.firstOrNull { localY >= it.offset && localY < it.offset + it.size }
                        ?: rows.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - localY) }
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
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                },
                                RoundedCornerShape(8.dp),
                            ),
                        )
                    }
                    items(list, key = { it.id }) { item ->
                        PantryRow(
                            name = item.name,
                            onDelete = {
                                viewModel.delete(item.id)
                                scope.launch {
                                    if (snackbar.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                                        viewModel.restore(item)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            }
        }
    }
}

// Block-based dragAndDropSource is deprecated but is the only variant that triggers on a
// real long-press (the plain-drag overloads fight the LazyColumn scroll) — same reasoning
// as the meal planner's PlannedRow and the shopping list's ShoppingRow.
@Suppress("DEPRECATION")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PantryRow(name: String, onDelete: () -> Unit) {
    // Captured here because the drag-shadow lambda below runs in DrawScope (no theme access).
    val shadowColor = MaterialTheme.colorScheme.primaryContainer
    // Same card chrome as a shopping-list row, so the two views match.
    Card(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth()
                // Long-press lifts the line as a drag source — dropping it on another
                // aisle teaches the categorization (pantry rows have no tap action).
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
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.shopping_delete))
            }
        }
    }
}
