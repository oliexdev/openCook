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

package com.food.opencook.ui.mealplan

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.food.opencook.R
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.AvailabilityBadge
import com.food.opencook.ui.components.CookedBadge
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.DateLabels
import com.food.opencook.util.MealTypes
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dwell before a dish drag hovering the other week's segment flips the visible week. */
private const val WEEK_SWITCH_DWELL_MS = 400L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MealPlanScreen(
    onOpenRecipe: (recipeId: String, planEntryId: String) -> Unit = { _, _ -> },
    onPickRecipe: (date: String, slot: String) -> Unit = { _, _ -> },
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    val options by viewModel.recipeOptions.collectAsStateWithLifecycle()
    val selectedWeek by viewModel.selectedWeek.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val generatedMsg = stringResource(R.string.mealplan_generated)
    val noRecipesMsg = stringResource(R.string.mealplan_no_recipes_suggest)
    val deletedMsg = stringResource(R.string.deleted)
    val undoMsg = stringResource(R.string.undo)
    val addedMsg = stringResource(R.string.shopping_added)

    // Self-heal on open: roll un-cooked but procured past dishes onto the next free day.
    // Idempotent, so running once per screen entry is enough — no daily confirmation.
    LaunchedEffect(Unit) { viewModel.reconcilePastDays() }

    var showSuggestConfirm by remember { mutableStateOf(false) }
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()

    val plannedMeals by viewModel.plannedMeals.collectAsStateWithLifecycle()
    // The slot column only earns its space once a day can hold more than one dish. With a
    // single meal planned the card stays exactly what it was before slots existed — unless
    // a one-off in a switched-off meal is on screen, which would otherwise be unlabelled.
    val showSlots = plannedMeals.size > 1 || week.any { it.slots.size > 1 }

    // Remove a dish with an Undo snackbar (re-adds it to the same cell).
    val removeDishWithUndo: (String, PlannedRecipe) -> Unit = { date, planned ->
        viewModel.remove(planned.entryId)
        scope.launch {
            if (snackbarHostState.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.addRecipe(date, planned.slot, planned.recipeId)
            }
        }
    }

    // "Suggest week" overwrites every day, so warn first when a plan exists.
    val hasPlan = week.any { it.entries.isNotEmpty() }
    val onSuggest: () -> Unit = {
        when {
            options.isEmpty() -> { scope.launch { snackbarHostState.showSnackbar(noRecipesMsg) } }
            hasPlan -> { showSuggestConfirm = true }
            else -> { viewModel.generateWeek() }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.mealplan_title),
                syncStatus = syncStatus,
                onSync = appBar::sync,
                actions = {
                    TooltipIcon(
                        tooltip = stringResource(R.string.mealplan_suggest_week),
                        icon = Icons.Outlined.AutoAwesome,
                        enabled = !generating,
                        onClick = onSuggest,
                    )
                    TooltipIcon(
                        tooltip = stringResource(R.string.mealplan_to_shopping),
                        icon = Icons.Outlined.AddShoppingCart,
                        enabled = !generating,
                        onClick = {
                            viewModel.generateShoppingList { scope.launch { snackbarHostState.showSnackbar(generatedMsg) } }
                        },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.screen).padding(top = Spacing.sm)) {
            if (generating) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Spacing.sm))
            }
            // True while a dish drag session is in flight (set by the list target below).
            // Arms the week selector as a spring-loaded switch and drives its highlight.
            val dishDragActive = remember { mutableStateOf(false) }

            WeekSelector(
                selected = selectedWeek,
                week = week,
                onSelect = viewModel::selectWeek,
                dishDragActive = dishDragActive.value,
            )
            Spacer(Modifier.height(Spacing.sm))

            // Drag-to-reschedule. The platform drag-and-drop never auto-scrolls a list, so a
            // SINGLE target on the LazyColumn drives everything itself: edge auto-scroll while a
            // dish is dragged near the top/bottom (so you can drag Sunday up to Monday when the
            // week doesn't fit on screen), plus hit-testing the drop position to the day under
            // the finger. One target also sidesteps nested drop-target dispatch ambiguity.
            val listState = rememberLazyListState()
            val listBounds = remember { mutableStateOf(Rect.Zero) }
            val hoveredCell = remember { mutableStateOf<String?>(null) }
            // Root-space bounds of every rendered meal row, keyed "date|slot". A day card now
            // holds several drop targets, so the LazyColumn's own item geometry (one entry per
            // day) is no longer fine-grained enough to hit-test against.
            val cellBounds = remember { mutableStateMapOf<String, Rect>() }
            val scrollSpeed = remember { mutableFloatStateOf(0f) }
            val edgeZonePx = with(LocalDensity.current) { 72.dp.toPx() }
            val maxStepPx = with(LocalDensity.current) { 18.dp.toPx() }

            // While the finger holds in an edge zone, scroll every frame at a ramped speed.
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { }
                    val v = scrollSpeed.floatValue
                    if (v != 0f) listState.scrollBy(v)
                }
            }

            val dropTarget = remember(listState) {
                // Map a root-space Y to the meal row under it; falls back to the nearest row so
                // drops in the inter-card gaps (or a slight overshoot) still land somewhere
                // sensible. Rows that scrolled out of view keep stale bounds in the map, so the
                // candidate set is clipped to what actually overlaps the visible list.
                fun cellAtY(y: Float): String? {
                    val visible = listBounds.value
                    val cells = cellBounds.entries.filter {
                        it.value.bottom > visible.top && it.value.top < visible.bottom
                    }
                    if (cells.isEmpty()) return null
                    return (
                        cells.firstOrNull { y >= it.value.top && y < it.value.bottom }
                            ?: cells.minByOrNull { kotlin.math.abs(it.value.center.y - y) }
                        )?.key
                }
                object : DragAndDropTarget {
                    override fun onStarted(event: DragAndDropEvent) {
                        dishDragActive.value = true
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
                        hoveredCell.value = cellAtY(e.y)
                    }
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        scrollSpeed.floatValue = 0f
                        hoveredCell.value = null
                        val e = event.toAndroidDragEvent()
                        val text = e.clipData?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString() ?: return false
                        val parts = text.split("|")
                        if (parts.size != 3) return false
                        val (entryId, fromDate, fromSlot) = parts
                        val target = (cellAtY(e.y) ?: return false).split("|")
                        if (target.size != 2) return false
                        val (toDate, toSlot) = target
                        if (fromDate == toDate && fromSlot == toSlot) return false
                        viewModel.moveDish(entryId, fromDate, fromSlot, toDate, toSlot)
                        return true
                    }
                    override fun onExited(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hoveredCell.value = null
                    }
                    override fun onEnded(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hoveredCell.value = null
                        dishDragActive.value = false
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .onGloballyPositioned { listBounds.value = it.boundsInRoot() }
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                        target = dropTarget,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(week, key = { it.date }) { day ->
                    DayCard(
                        day = day,
                        plannedMeals = plannedMeals,
                        showSlots = showSlots,
                        hoveredCell = hoveredCell.value,
                        onCellBounds = { key, rect -> cellBounds[key] = rect },
                        onAdd = { slot -> onPickRecipe(day.date, slot) },
                        onFillDay = { viewModel.fillDay(day.date) },
                        onRemoveDish = removeDishWithUndo,
                        onAddToShopping = { planned ->
                            viewModel.addToShoppingList(planned, day.date) {
                                scope.launch { snackbarHostState.showSnackbar(addedMsg) }
                            }
                        },
                        onOpenRecipe = onOpenRecipe,
                    )
                }
            }
        }
    }

    if (showSuggestConfirm) {
        AlertDialog(
            onDismissRequest = { showSuggestConfirm = false },
            title = { Text(stringResource(R.string.mealplan_regenerate_title)) },
            text = { Text(stringResource(R.string.mealplan_regenerate_text)) },
            confirmButton = {
                Button(onClick = { showSuggestConfirm = false; viewModel.generateWeek() }) {
                    Text(stringResource(R.string.mealplan_regenerate_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuggestConfirm = false }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekSelector(
    selected: WeekSelection,
    week: List<DayPlan>,
    onSelect: (WeekSelection) -> Unit,
    dishDragActive: Boolean = false,
) {
    // Spring-loaded week switch (launcher-page style): while a dish drag hovers the
    // *other* week's segment, a short dwell flips the visible week — the system drag
    // session survives the recomposition, so the user just carries on to the target
    // day. The switch is symmetric, and a graze shorter than the dwell does nothing.
    var hoveredWeek by remember { mutableStateOf<WeekSelection?>(null) }
    LaunchedEffect(hoveredWeek, selected) {
        val target = hoveredWeek ?: return@LaunchedEffect
        if (target == selected) return@LaunchedEffect
        delay(WEEK_SWITCH_DWELL_MS)
        onSelect(target)
    }

    Column(Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(
                WeekSelection.CURRENT to stringResource(R.string.mealplan_week_current),
                WeekSelection.NEXT to stringResource(R.string.mealplan_week_next),
            )
            options.forEachIndexed { index, (value, label) ->
                val segmentTarget = remember(value) {
                    object : DragAndDropTarget {
                        override fun onEntered(event: DragAndDropEvent) { hoveredWeek = value }
                        override fun onExited(event: DragAndDropEvent) {
                            if (hoveredWeek == value) hoveredWeek = null
                        }
                        // A drop on the segment itself is deliberately unhandled — the
                        // segment only switches the view; placing happens on a day card.
                        override fun onDrop(event: DragAndDropEvent) = false
                        override fun onEnded(event: DragAndDropEvent) { hoveredWeek = null }
                    }
                }
                // While a dish is dragged, the inactive segment advertises itself as the
                // way over ("you can go here") in the same secondaryContainer used for
                // the day-card drop highlight; a bit stronger once actually hovered.
                val armed = dishDragActive && selected != value
                val colors = when {
                    armed && hoveredWeek == value -> SegmentedButtonDefaults.colors(
                        inactiveContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        inactiveContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    armed -> SegmentedButtonDefaults.colors(
                        inactiveContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    )
                    else -> SegmentedButtonDefaults.colors()
                }
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = colors,
                    modifier = Modifier.dragAndDropTarget(
                        shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                        target = segmentTarget,
                    ),
                ) { Text(label) }
            }
        }
        // Spell out the actual Mon–Sun range so the user always knows which days the
        // toggle currently maps to — segmented control alone could be ambiguous mid-week.
        if (week.isNotEmpty()) {
            val first = runCatching { LocalDate.parse(week.first().date) }.getOrNull()
            val last = runCatching { LocalDate.parse(week.last().date) }.getOrNull()
            if (first != null && last != null) {
                val fmt = remember { DateLabels.weekdayDayMonth() }
                Text(
                    text = stringResource(R.string.mealplan_week_range, first.format(fmt), last.format(fmt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun DayCard(
    day: DayPlan,
    plannedMeals: List<String>,
    showSlots: Boolean,
    hoveredCell: String?,
    onCellBounds: (String, Rect) -> Unit,
    onAdd: (String) -> Unit,
    onFillDay: () -> Unit,
    onRemoveDish: (String, PlannedRecipe) -> Unit,
    onAddToShopping: (PlannedRecipe) -> Unit,
    onOpenRecipe: (recipeId: String, planEntryId: String) -> Unit,
) {
    val today = LocalDate.now().toString()
    val isToday = day.date == today
    // Past = the day has gone by; only then does "cooked yet?" make sense.
    val isPast = day.date < today
    // Meals the household doesn't plan by default, offered as a one-off for this day only.
    val extraSlots = MealTypes.KEYS.filterNot { it in plannedMeals || day.slots.any { s -> s.slot == it } }
    val hasGaps = day.slots.any { it.dishes.isEmpty() && it.hasCandidates }
    // On today's card, mark the meal the clock is in — the one row that matters right now.
    val nowSlot = if (isToday) MealPlanSlots.currentSlot(plannedMeals, LocalTime.now().hour) else null

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Today used to flood the whole card with primaryContainer. That was fine for a
            // single row; across four it drowns the content, so today is now marked by an
            // outline and a coloured date instead — emphasis without a wall of colour.
            containerColor = if (isToday) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    day.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // One wand per day instead of one per empty row: a fully empty week had
                    // 28 of them, which read as clutter rather than as an offer.
                    if (hasGaps && !isPast) {
                        TooltipIcon(
                            tooltip = stringResource(R.string.mealplan_fill_day),
                            icon = Icons.Outlined.AutoAwesome, // same "magic" icon as "suggest week"
                            onClick = onFillDay,
                            size = 36.dp,
                        )
                    }
                }
            }
            // The day itself: the meals the household actually plans, in order of the day.
            val slotRow: @Composable (SlotPlan) -> Unit = { slotPlan ->
                val key = "${day.date}|${slotPlan.slot}"
                SlotRow(
                    slotPlan = slotPlan,
                    date = day.date,
                    showSlotLabel = showSlots,
                    isDropTarget = hoveredCell == key,
                    isPast = isPast,
                    isNow = slotPlan.slot == nowSlot,
                    modifier = Modifier.onGloballyPositioned { onCellBounds(key, it.boundsInRoot()) },
                    onAdd = { onAdd(slotPlan.slot) },
                    onRemoveDish = onRemoveDish,
                    onAddToShopping = onAddToShopping,
                    onOpenRecipe = onOpenRecipe,
                )
            }
            day.slots.filterNot { it.isExtra }.forEach { slotRow(it) }

            // Everything the household does *not* plan lives below the day, behind a hairline:
            // meals already filled as a one-off, then an offer for the rest. They stay down
            // here even once filled — the selection defines the shape of a normal day, so a
            // Sunday cake must not wedge itself between lunch and dinner. Enabling the meal in
            // the settings is what moves it up into its chronological place.
            val extraRows = day.slots.filter { it.isExtra }
            val showChips = extraSlots.isNotEmpty() && !isPast
            if (extraRows.isNotEmpty() || showChips) {
                HorizontalDivider(
                    Modifier.padding(vertical = Spacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                extraRows.forEach { slotRow(it) }
                if (showChips) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        extraSlots.forEach { slot ->
                            AssistChip(
                                onClick = { onAdd(slot) },
                                label = {
                                    Text(
                                        "＋ " + stringResource(MealPlanSlots.shortLabelRes(slot)),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One meal of one day: the planned dish (or dishes), or an empty "plan this" line. */
@Composable
private fun SlotRow(
    slotPlan: SlotPlan,
    date: String,
    showSlotLabel: Boolean,
    isDropTarget: Boolean,
    isPast: Boolean,
    isNow: Boolean,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onRemoveDish: (String, PlannedRecipe) -> Unit,
    onAddToShopping: (PlannedRecipe) -> Unit,
    onOpenRecipe: (recipeId: String, planEntryId: String) -> Unit,
) {
    // Highlighted while a dragged dish hovers this exact cell — the feedback has to be per
    // meal now, otherwise a four-row card would light up as one big undifferentiated target.
    val background = if (isDropTarget) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    // Which meal this is rides in the status line rather than in a leading column: a 52dp
    // label column ate a sixth of the width before the thumbnail even started, and the
    // status line under the title was already there and had room to spare.
    val slotLabel = if (showSlotLabel) stringResource(MealPlanSlots.shortLabelRes(slotPlan.slot)) else null
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background),
    ) {
        if (slotPlan.dishes.isEmpty()) {
            GhostRow(slotPlan, slotLabel, onAdd = onAdd)
        } else {
            slotPlan.dishes.forEach { planned ->
                // A past day that was never confirmed cooked is shown faded: it's over,
                // and the app makes no assumption about whether it actually happened.
                PlannedRow(
                    planned = planned,
                    fromDate = date,
                    slotLabel = slotLabel,
                    isNow = isNow,
                    onOpenRecipe = onOpenRecipe,
                    faded = isPast && !planned.cooked,
                    onRemove = { onRemoveDish(date, planned) },
                    onAddToShopping = { onAddToShopping(planned) },
                )
            }
        }
    }
}

/**
 * An unplanned meal: one quiet line, roughly a third of a dish row's weight. The gaps in the
 * week have to be *findable* without competing with what's actually planned, so this carries
 * no icon of its own — the whole line is the tap target, and the day's wand in the card
 * header fills every gap at once.
 */
@Composable
private fun GhostRow(slotPlan: SlotPlan, slotLabel: String?, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = slotPlan.hasCandidates, onClick = onAdd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (slotPlan.hasCandidates) {
            // The empty line is where the meal gets *named* — there is nothing else on it,
            // and it's what tells you which gap you are about to fill.
            Text(
                "＋ " + if (slotLabel != null) stringResource(R.string.mealplan_slot_add_named, slotLabel)
                else stringResource(R.string.mealplan_slot_add),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Nothing in the library is marked for this meal — say so instead of opening
            // an empty picker and letting the user wonder what they did wrong.
            Text(
                stringResource(
                    R.string.mealplan_slot_no_recipes,
                    stringResource(MealTypes.labelRes(slotPlan.slot)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Block-based dragAndDropSource is deprecated but is the only variant that triggers on a
// real long-press (the transferData overloads start on a plain drag, which the LazyColumn
// scroll would consume). Functionally correct; suppress the deprecation noise.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlannedRow(
    planned: PlannedRecipe,
    fromDate: String,
    onOpenRecipe: (recipeId: String, planEntryId: String) -> Unit,
    slotLabel: String? = null,
    isNow: Boolean = false,
    faded: Boolean = false,
    onRemove: () -> Unit = {},
    onAddToShopping: () -> Unit = {},
) {
    var showWhy by remember { mutableStateOf(false) }
    // Captured here because the drag-shadow lambda below runs in DrawScope (no theme access).
    val shadowColor = MaterialTheme.colorScheme.primaryContainer
    Row(
        Modifier.fillMaxWidth().alpha(if (faded) 0.5f else 1f)
            // ONE detector handles both gestures so they don't fight: a tap opens the recipe,
            // a long-press lifts the dish as a drag source ("entryId|date" payload). A separate
            // .clickable used to win the gesture race and swallow the long-press, so it's gone.
            // The explicit drawDragDecoration draws the shadow instead of snapshotting the live
            // row — that auto-snapshot intermittently blanked the dish image + name on screen.
            .dragAndDropSource(
                drawDragDecoration = {
                    drawRoundRect(color = shadowColor, cornerRadius = CornerRadius(16.dp.toPx()))
                },
                block = {
                    detectTapGestures(
                        onTap = { onOpenRecipe(planned.recipeId, planned.entryId) },
                        onLongPress = {
                            startTransfer(
                                DragAndDropTransferData(
                                    ClipData.newPlainText("dish", "${planned.entryId}|$fromDate|${planned.slot}"),
                                ),
                            )
                        },
                    )
                },
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (planned.imageModel != null) {
            AsyncImage(
                model = planned.imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            // A bare colour block reads as a loading error once several rows stack up;
            // the glyph makes it obviously "recipe without a photo" (same as the plan sheet).
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            // The title gets the full width of its own line. Squeezing the two action icons
            // beside it leaves ~108dp for the name on a 360dp phone — so status and actions
            // share the second line instead, which also keeps the icons well clear of the
            // title's tap area (the whole row is a drag source on long-press).
            Text(planned.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (slotLabel != null) {
                    // The meal that's happening now reads in the accent colour, so a glance
                    // at today's card lands on the right row without counting from the top.
                    Text(
                        "$slotLabel ·",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isNow) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                // Once cooked, missing ingredients are moot — show only the "cooked" badge.
                if (planned.cooked) {
                    CookedBadge()
                } else {
                    AvailabilityBadge(missingCount = planned.missing, missingItems = planned.missingItems)
                }
                if (planned.reasons.isNotEmpty()) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.mealplan_reasons_why)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = { showWhy = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = stringResource(R.string.mealplan_reasons_why),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Appears exactly when it's useful: the badge next to it just said what's
                // missing, this puts it on the list without a detour through the recipe.
                if (planned.missing > 0 && !planned.cooked) {
                    TooltipIcon(
                        tooltip = stringResource(R.string.mealplan_add_missing),
                        icon = Icons.Outlined.AddShoppingCart,
                        onClick = onAddToShopping,
                        size = 32.dp,
                    )
                }
                TooltipIcon(
                    tooltip = stringResource(R.string.mealplan_remove_dish),
                    icon = Icons.Outlined.DeleteOutline,
                    onClick = onRemove,
                    size = 32.dp,
                )
            }
        }
    }
    if (showWhy) WhyBottomSheet(planned, onDismiss = { showWhy = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhyBottomSheet(planned: PlannedRecipe, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.lg)) {
            // Hero row: big rounded image + recipe name as title — gives the sheet the same
            // visual anchor as the day-card row, so the user knows exactly which dish this
            // explanation is about even after scrolling the page.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (planned.imageModel != null) {
                    AsyncImage(
                        model = planned.imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer))
                }
                Column(Modifier.weight(1f)) {
                    Text(planned.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.mealplan_reasons_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))

            val (pos, neg) = MealPlanReasons.split(planned.reasons)
            if (pos.isEmpty() && neg.isEmpty()) {
                Text(
                    stringResource(R.string.mealplan_reasons_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (pos.isNotEmpty()) {
                    ReasonSection(
                        title = stringResource(R.string.mealplan_reasons_section_for),
                        items = pos,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (neg.isNotEmpty()) {
                    if (pos.isNotEmpty()) Spacer(Modifier.height(Spacing.md))
                    ReasonSection(
                        title = stringResource(R.string.mealplan_reasons_section_against),
                        items = neg,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

/** A titled tonal card holding the per-factor rows. Drops the "•" bullet style for a
 *  proper icon row — same icons as the chips, so the visual language carries through. */
@Composable
private fun ReasonSection(
    title: String,
    items: List<MealPlanner.ReasonContribution>,
    container: Color,
    onContainer: Color,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.xs),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            items.forEachIndexed { i, c ->
                if (i > 0) Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(MealPlanReasons.icon(c.code), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(MealPlanReasons.text(c), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Icon button labelled by a long-press tooltip — keeps the board tidy while making
 * each action's meaning discoverable (e.g. the "keep" star, "skip day"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIcon(
    tooltip: String,
    icon: ImageVector,
    onClick: () -> Unit,
    filled: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
    /** Button diameter — the dish rows use a compact 32.dp so status and two actions fit
     *  on one line; null keeps the Material default (48.dp) for the top-bar actions. */
    size: Dp? = null,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        val sizeModifier = if (size != null) Modifier.size(size) else Modifier
        if (filled) {
            FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = sizeModifier) {
                Icon(icon, contentDescription = tooltip)
            }
        } else {
            IconButton(onClick = onClick, enabled = enabled, modifier = sizeModifier) {
                Icon(
                    icon,
                    contentDescription = tooltip,
                    tint = tint ?: LocalContentColor.current,
                    modifier = if (size != null) Modifier.size(20.dp) else Modifier,
                )
            }
        }
    }
}
