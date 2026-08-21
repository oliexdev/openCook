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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.pluralStringResource
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
import com.food.opencook.ui.components.SwipeActionRow
import com.food.opencook.ui.retrospect.monthName
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.MealTypes
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/** Height of a sticky week header — subtracted from the list's top edge when hit-testing a
 *  drop, so a dish released on the header doesn't land on the row hidden underneath it. */
private val WEEK_HEADER_HEIGHT = 40.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MealPlanScreen(
    onOpenRecipe: (recipeId: String, planEntryId: String) -> Unit = { _, _ -> },
    onPickRecipe: (date: String, slot: String) -> Unit = { _, _ -> },
    onOpenRetrospect: () -> Unit = {},
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    val retrospect by viewModel.retrospect.collectAsStateWithLifecycle()
    val options by viewModel.recipeOptions.collectAsStateWithLifecycle()
    val todayKey by viewModel.todayKey.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val generatedMsg = stringResource(R.string.mealplan_generated)
    val deletedMsg = stringResource(R.string.deleted)
    val undoMsg = stringResource(R.string.undo)
    val addedMsg = stringResource(R.string.shopping_added)
    val alreadyOnListMsg = stringResource(R.string.mealplan_already_on_list)

    // Self-heal on open: roll un-cooked but procured past dishes onto the next free day.
    // Idempotent, so running once per screen entry is enough — no daily confirmation.
    // Re-anchoring the window on the same pass keeps a phone left on this screen overnight
    // from insisting that yesterday is today.
    //
    // The rolling fill runs *after* the reconcile, so a dish that just rolled forward counts
    // as occupying its new day and the planner doesn't plan on top of it.
    LaunchedEffect(Unit) {
        viewModel.refreshToday()
        viewModel.reconcilePastDays()
        viewModel.autoFillWindow()
    }

    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()

    val plannedMeals by viewModel.plannedMeals.collectAsStateWithLifecycle()
    // The slot column only earns its space once a day can hold more than one dish. With a
    // single meal planned the card stays exactly what it was before slots existed — unless
    // a one-off in a switched-off meal is on screen, which would otherwise be unlabelled.
    val showSlots = plannedMeals.size > 1 || week.any { it.slots.size > 1 }

    // Remove a dish with an Undo snackbar. Deleting also pulls the shopping lines this dish
    // alone put on the list for that day, and the Undo puts both halves back together.
    val removeDishWithUndo: (String, PlannedRecipe) -> Unit = { date, planned ->
        viewModel.remove(planned, date)
        scope.launch {
            if (snackbarHostState.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.undoRemove(planned, date)
            }
        }
    }

    // Put a dish's ingredients on the shopping list, with an Undo — a right swipe is easy to
    // make by accident, and pouring a whole recipe onto the list is not something the user
    // should have to unpick line by line.
    //
    // The add is idempotent per (dish, day), so a repeat lands on nothing. Saying "added"
    // then — with no Undo next to it — reads as a bug: the user is told something happened
    // and offered no way back. So the second time it says what is actually true.
    val addToShoppingWithUndo: (PlannedRecipe, String) -> Unit = { planned, date ->
        viewModel.addToShoppingList(planned, date) { added ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = if (added) addedMsg else alreadyOnListMsg,
                    actionLabel = if (added) undoMsg else null,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoAddToShoppingList()
            }
        }
    }

    // The rolling list, cut into calendar weeks for the sticky headers. The index of today is
    // computed alongside them because headers are list items too — the jump-on-open and the
    // "today" button have to agree on the same number.
    val listState = rememberLazyListState()
    val (sections, todayIndex) = remember(week, todayKey) { sectionsOf(week, todayKey) }

    // Land on today, once per screen entry. Keying this on the data instead would yank the
    // list back to today every time a sync or an edit re-emits the week.
    //
    // The negative offset is not cosmetic: scrolling to an item puts its top edge at the very
    // top of the viewport, which is exactly where the sticky week header floats — so the
    // card's date line ended up hidden underneath it. Stopping one header short leaves the
    // day it just scrolled to actually readable.
    val headerOffsetPx = with(LocalDensity.current) { -WEEK_HEADER_HEIGHT.roundToPx() }
    var jumpedToToday by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(todayIndex) {
        if (!jumpedToToday && todayIndex >= 0) {
            listState.scrollToItem(todayIndex, headerOffsetPx)
            jumpedToToday = true
        }
    }
    // Demonstrate the swipe once, on exactly one row — same convention as the pantry and the
    // shopping list. The chosen row is the first dish from today onwards rather than the very
    // first in the list: last week's entries are usually scrolled out of sight, and a hint
    // that plays off-screen is a hint nobody gets.
    val swipeHintSeen by viewModel.swipeHintSeen.collectAsStateWithLifecycle()
    val peekDish = if (swipeHintSeen) null else week
        .firstOrNull { it.date >= todayKey && it.entries.isNotEmpty() }
        ?.entries?.firstOrNull()?.entryId

    // Only offer the way back once today has actually left the viewport. Keyed on the index
    // because it is a plain value — an unkeyed remember would pin the derivation to the −1 of
    // the first composition, before the week had loaded.
    val todayOffScreen by remember(todayIndex, todayKey) {
        derivedStateOf {
            todayIndex >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.key == todayKey }
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
        floatingActionButton = {
            AnimatedVisibility(todayOffScreen, enter = fadeIn(), exit = fadeOut()) {
                val label = stringResource(R.string.mealplan_jump_today)
                ExtendedFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(todayIndex, headerOffsetPx) } },
                    icon = { Icon(Icons.Outlined.Today, contentDescription = null) },
                    text = { Text(label) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.screen).padding(top = Spacing.sm)) {
            if (generating) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Spacing.sm))
            }

            // Drag-to-reschedule. The platform drag-and-drop never auto-scrolls a list, so a
            // SINGLE target on the LazyColumn drives everything itself: edge auto-scroll while a
            // dish is dragged near the top/bottom (which is now the *only* way across a week
            // boundary — the list is continuous, so there is no page to flip), plus hit-testing
            // the drop position to the day under the finger. One target also sidesteps nested
            // drop-target dispatch ambiguity.
            val listBounds = remember { mutableStateOf(Rect.Zero) }
            val hoveredCell = remember { mutableStateOf<String?>(null) }
            // Root-space bounds of every rendered meal row, keyed "date|slot". A day card now
            // holds several drop targets, so the LazyColumn's own item geometry (one entry per
            // day) is no longer fine-grained enough to hit-test against.
            val cellBounds = remember { mutableStateMapOf<String, Rect>() }
            val scrollSpeed = remember { mutableFloatStateOf(0f) }
            val edgeZonePx = with(LocalDensity.current) { 72.dp.toPx() }
            val maxStepPx = with(LocalDensity.current) { 18.dp.toPx() }
            val headerPx = with(LocalDensity.current) { WEEK_HEADER_HEIGHT.toPx() }

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
                // candidate set is clipped to what actually overlaps the visible list — minus
                // the strip a sticky week header covers, since the row hiding under it is not
                // something the user can see, let alone aim at.
                fun cellAtY(y: Float): String? {
                    val visible = listBounds.value
                    val top = visible.top + headerPx
                    val cells = cellBounds.entries.filter {
                        it.value.bottom > top && it.value.top < visible.bottom
                    }
                    if (cells.isEmpty()) return null
                    return (
                        cells.firstOrNull { y >= it.value.top && y < it.value.bottom }
                            ?: cells.minByOrNull { kotlin.math.abs(it.value.center.y - y) }
                        )?.key
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
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                // Clearance for the "today" button, which is on screen exactly when the user
                // has scrolled to one of the two ends of the list.
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                // Above the oldest day, so it is met by scrolling back rather than looked
                // for in a menu — the list opens on today, so it starts out of sight.
                retrospect?.let { teaser ->
                    item(key = "retrospect") { RetrospectCard(teaser, onOpenRetrospect) }
                }
                sections.forEach { section ->
                    stickyHeader(key = "week_${section.weekOffset}") {
                        WeekHeader(section.weekOffset)
                    }
                    items(section.days, key = { it.date }) { day ->
                        DayCard(
                            day = day,
                            today = todayKey,
                            plannedMeals = plannedMeals,
                            showSlots = showSlots,
                            hoveredCell = hoveredCell.value,
                            peekDish = peekDish,
                            onPeekShown = viewModel::markSwipeHintSeen,
                            onCellBounds = { key, rect -> cellBounds[key] = rect },
                            onAdd = { slot -> onPickRecipe(day.date, slot) },
                            onRemoveDish = removeDishWithUndo,
                            onAddToShopping = { planned -> addToShoppingWithUndo(planned, day.date) },
                            onOpenRecipe = onOpenRecipe,
                        )
                    }
                }
            }
        }
    }

}

/** "Yesterday" / "Today" / "Tomorrow" for the three days around [today], null for the rest —
 *  those carry their date and their week header, which is orientation enough. */
private fun relativeDayRes(date: String, today: String): Int? {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    val now = runCatching { LocalDate.parse(today) }.getOrNull() ?: return null
    return when (ChronoUnit.DAYS.between(now, day)) {
        -1L -> R.string.mealplan_day_yesterday
        0L -> R.string.mealplan_day_today
        1L -> R.string.mealplan_day_tomorrow
        else -> null
    }
}

/**
 * Sticky separator between calendar weeks. The day cards carry their own dates, so this is
 * not about naming the days — it is about keeping "am I looking at last week or next week?"
 * answerable at a glance while scrolling a continuous fifteen-day list.
 */
/**
 * The way into the retrospective, sitting above the oldest day the list reaches.
 *
 * It carries today's card treatment — surfaceVariant under a terracotta outline — because in
 * this list an outline already means "not merely a day". The two can never be on screen at
 * once: they are a week apart.
 */
@Composable
private fun RetrospectCard(
    teaser: MealPlanViewModel.RetrospectTeaser,
    onClick: () -> Unit,
) {
    val month = monthName(LocalDate.now().month)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.retrospect_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    pluralStringResource(
                        R.plurals.retrospect_entry_subtitle,
                        teaser.monthCooked,
                        month,
                        teaser.monthCooked,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
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
private fun WeekHeader(weekOffset: Int) {
    val label = when (weekOffset) {
        -1 -> stringResource(R.string.mealplan_week_last)
        0 -> stringResource(R.string.mealplan_week_current)
        1 -> stringResource(R.string.mealplan_week_next)
        else -> stringResource(R.string.mealplan_week_after_next)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(WEEK_HEADER_HEIGHT)
            // Opaque on purpose: a sticky header floats over the cards scrolling beneath it,
            // and a translucent one would let a dish photo bleed through the label.
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            label,
            // One step below the day date (titleMedium) — a header that sits on screen
            // permanently has to orient, not compete. The weight comes from full text
            // contrast rather than from size: the weeks still to come read as live, the one
            // behind us stays dimmed. Colour is deliberately not used here — the accent
            // belongs to today's card alone, and two primary signals stacked on top of each
            // other cancel each other out.
            style = MaterialTheme.typography.titleSmall,
            color = if (weekOffset < 0) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        // Hairline, so the cards visibly run *under* the header instead of butting into it.
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
    }
}

@Composable
private fun DayCard(
    day: DayPlan,
    today: String,
    plannedMeals: List<String>,
    showSlots: Boolean,
    hoveredCell: String?,
    peekDish: String?,
    onPeekShown: () -> Unit,
    onCellBounds: (String, Rect) -> Unit,
    onAdd: (String) -> Unit,
    onRemoveDish: (String, PlannedRecipe) -> Unit,
    onAddToShopping: (PlannedRecipe) -> Unit,
    onOpenRecipe: (recipeId: String, planEntryId: String) -> Unit,
) {
    val isToday = day.date == today
    // Past = the day has gone by; only then does "cooked yet?" make sense.
    val isPast = day.date < today
    // Meals the household doesn't plan by default, offered as a one-off for this day only.
    val extraSlots = MealTypes.KEYS.filterNot { it in plannedMeals || day.slots.any { s -> s.slot == it } }
    // On today's card, mark the meal the clock is in — the one row that matters right now.
    val nowSlot = if (isToday) MealPlanSlots.currentSlot(plannedMeals, LocalTime.now().hour) else null

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Today used to flood the whole card with primaryContainer. That was fine for a
            // single row; across four it drowns the content, so today is now marked by an
            // outline and a coloured date instead — emphasis without a wall of colour.
            // Days behind us sink one tier: they are there to be read, not worked on. That
            // tier used to be byte-identical to `background` in the light palette, which made
            // such a card invisible — the palette now gives it its own step.
            // Ordinary days stand on the same ground as the shopping and pantry rows, so a
            // card weighs the same wherever you meet it. Today steps *lighter* rather than
            // darker: the tier below it was half a lightness point away from an ordinary row,
            // which is no distinction at all, while a lighter card reads as the raised, current
            // one — and carries the outline, the coloured date and the word "Today" besides.
            containerColor = if (isToday) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .alpha(if (isPast) 0.75f else 1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    day.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.primary
                        isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color.Unspecified
                    },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    // "Yesterday / Today / Tomorrow" next to the date. Over a fifteen-day list
                    // a coloured outline can only mark one day; a word also places the two
                    // days either side of it, survives the dark theme unchanged, and is the
                    // only part of this that a screen reader can announce.
                    relativeDayRes(day.date, today)?.let { res ->
                        Text(
                            stringResource(res),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // A day behind us holding nothing at all. Empty rows are stripped from
                    // past days, so the card would otherwise be a date and a void. It rides
                    // on the date's own line rather than below it: seven of these open the
                    // list, and a second line each would cost most of a screen for three
                    // words. It says "not planned" rather than "not cooked" — all the app
                    // knows is that no plan existed; the household may well have eaten and
                    // simply never written it down.
                    if (isPast && day.slots.isEmpty()) {
                        Text(
                            stringResource(R.string.mealplan_day_not_planned),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    peekDish = peekDish,
                    onPeekShown = onPeekShown,
                    modifier = Modifier.onGloballyPositioned { onCellBounds(key, it.boundsInRoot()) },
                    onAdd = { onAdd(slotPlan.slot) },
                    onRemoveDish = onRemoveDish,
                    onAddToShopping = onAddToShopping,
                    onOpenRecipe = onOpenRecipe,
                )
            }
            // Hairlines between the meals of a day: the day is the container, the meals are
            // its rows. Drawn across the whole card rather than inset past the thumbnail —
            // an inset rule only lines up while every row *has* a thumbnail, and an empty
            // "plan this" row has none. Nothing above the first or below the last: a rule
            // there would fight the card's own edge.
            //
            // Colour: `outlineVariant` sits three lightness points off the card in this
            // palette, which at one pixel is not a line anybody sees; full `outline` at nine
            // points draws more attention than a separator should. Two thirds of `outline`
            // lands around five — present at a glance, silent when you are reading.
            day.slots.filterNot { it.isExtra }.forEachIndexed { index, slotPlan ->
                if (index > 0) {
                    HorizontalDivider(
                        Modifier.padding(bottom = Spacing.xs),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA),
                    )
                }
                slotRow(slotPlan)
            }

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
                    color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA),
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
    peekDish: String?,
    onPeekShown: () -> Unit,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onRemoveDish: (String, PlannedRecipe) -> Unit,
    onAddToShopping: (PlannedRecipe) -> Unit,
    onOpenRecipe: (recipeId: String, planEntryId: String) -> Unit,
) {
    // Highlighted while a dragged dish hovers this exact cell — the feedback has to be per
    // meal now, otherwise a four-row card would light up as one big undifferentiated target.
    // Transient drag feedback, so the primary family rather than the green of a food state.
    val background = if (isDropTarget) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
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
        // Every row carries the same swap button, empty or filled, and it always opens the
        // picker — which leads with the planner's own proposal. One destination instead of a
        // wand that decided blindly next to a line that decided nothing.
        if (slotPlan.dishes.isEmpty()) {
            GhostRow(slotPlan, slotLabel, onAdd = onAdd)
        } else {
            slotPlan.dishes.forEach { planned ->
                // Removing is a swipe (left), the same gesture as in the shopping and pantry
                // lists — so the row itself carries only what it *is*, not a permanent bin
                // icon per dish. The undo snackbar makes the gesture safe to fumble.
                SwipeActionRow(
                    onDelete = { onRemoveDish(date, planned) },
                    onAction = { onAddToShopping(planned) },
                    actionIcon = Icons.Outlined.AddShoppingCart,
                    actionLabel = stringResource(R.string.mealplan_add_missing),
                    onConfirm = { _, _ -> },
                    // The dish stays planned — a right swipe shops for it, it does not
                    // remove it from the day. So the row snaps back instead of sliding off.
                    actionRemovesRow = false,
                    peek = peekDish == planned.entryId,
                    onPeekShown = onPeekShown,
                ) {
                    // A past day that was never confirmed cooked is shown faded: it's over,
                    // and the app makes no assumption about whether it actually happened.
                    PlannedRow(
                        planned = planned,
                        fromDate = date,
                        slotLabel = slotLabel,
                        isNow = isNow,
                        onOpenRecipe = onOpenRecipe,
                        faded = isPast && !planned.cooked,
                        onSwap = onAdd,
                    )
                }
            }
        }
    }
}

/**
 * Dish thumbnail. Sized to the text block beside it — a 20dp title line plus a 32dp status
 * row — so the two share a bottom edge. It used to be 48dp, four short, which left the
 * status line hanging past the picture.
 */
private val ROW_THUMB = 52.dp

/** Separator strength. `outlineVariant` is invisible on this palette and full `outline` is
 *  louder than a separator should be; this lands between them. */
private const val DIVIDER_ALPHA = 0.65f


/**
 * The row's swap control: opens the picker for this cell, proposal first.
 *
 * Deliberately a **neutral** container. The tonal default is `secondaryContainer`, which in
 * this palette already carries "ready / done" — the availability badge, the cooked marker, the
 * positive swipe. A control that repeats on every single row is the worst possible place to
 * spend a colour that means something, so it takes the one surface that means nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapButton(onClick: () -> Unit) {
    val label = stringResource(R.string.mealplan_swap_dish)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.padding(start = Spacing.sm, end = Spacing.xs).size(32.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = label, modifier = Modifier.size(18.dp))
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
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onAdd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The empty line is where the meal gets *named* — there is nothing else on it, and
        // it's what tells you which gap you are about to fill. It offers to fill the gap
        // even when the library holds nothing marked for this meal: the picker opens with a
        // removable filter chip, so "no breakfasts yet" is one tap from "here is everything"
        // — which is a far better answer than a dead line saying no.
        Text(
            "＋ " + if (slotLabel != null) stringResource(R.string.mealplan_slot_add_named, slotLabel)
            else stringResource(R.string.mealplan_slot_add),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        // The same button in the same place as on a filled row, so the column runs straight
        // down the card and one control means one thing everywhere. The line itself stays
        // tappable — it is the larger target and leads to exactly the same screen.
        SwapButton(onAdd)
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
    onSwap: () -> Unit = {},
) {
    var showWhy by remember { mutableStateOf(false) }
    // Captured here because the drag-shadow lambda below runs in DrawScope (no theme access).
    val shadowColor = MaterialTheme.colorScheme.surfaceContainerHighest
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
        // Top-aligned, not centred: the title plus its status line is taller than the
        // thumbnail, so centring pushed the title's cap line above the photo's top edge.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (planned.imageModel != null) {
            AsyncImage(
                model = planned.imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ROW_THUMB).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            // A bare colour block reads as a loading error once several rows stack up;
            // the glyph makes it obviously "recipe without a photo" (same as the plan sheet).
            Box(
                Modifier.size(ROW_THUMB).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                // The same tone as the cart standing next to it. The badge
                                // only draws a cart in the "something is missing" state — the
                                // stocked state is a green tick — so `tertiary` matches it
                                // whenever a cart is on the row at all.
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Swap this dish for another. A filled tonal circle rather than a bare glyph:
                // it is the row's only control now that shopping moved to the swipe, and on a
                // line otherwise made of status text a naked icon reads as one more status.
                SwapButton(onSwap)
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
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
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
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
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
