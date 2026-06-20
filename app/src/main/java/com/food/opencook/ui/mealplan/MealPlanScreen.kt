package com.food.opencook.ui.mealplan

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.food.opencook.R
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.AvailabilityBadge
import com.food.opencook.ui.components.CookedBadge
import com.food.opencook.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MealPlanScreen(
    onOpenRecipe: (String) -> Unit = {},
    onPickRecipe: (String) -> Unit = {},
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

    // Self-heal on open: roll un-cooked but procured past dishes onto the next free day.
    // Idempotent, so running once per screen entry is enough — no daily confirmation.
    LaunchedEffect(Unit) { viewModel.reconcilePastDays() }

    var showSuggestConfirm by remember { mutableStateOf(false) }
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()

    // Remove a day's dish with an Undo snackbar (re-adds it to the same day).
    val removeDishWithUndo: (String, PlannedRecipe) -> Unit = { date, planned ->
        viewModel.remove(planned.entryId)
        scope.launch {
            if (snackbarHostState.showSnackbar(deletedMsg, undoMsg, withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.addRecipe(date, planned.recipeId)
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
            WeekSelector(
                selected = selectedWeek,
                week = week,
                onSelect = viewModel::selectWeek,
            )
            Spacer(Modifier.height(Spacing.sm))

            // Drag-to-reschedule. The platform drag-and-drop never auto-scrolls a list, so a
            // SINGLE target on the LazyColumn drives everything itself: edge auto-scroll while a
            // dish is dragged near the top/bottom (so you can drag Sunday up to Monday when the
            // week doesn't fit on screen), plus hit-testing the drop position to the day under
            // the finger. One target also sidesteps nested drop-target dispatch ambiguity.
            val listState = rememberLazyListState()
            val listBounds = remember { mutableStateOf(Rect.Zero) }
            val hoveredDate = remember { mutableStateOf<String?>(null) }
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
                // Map a root-space Y to the day under it; falls back to the nearest day so drops
                // in the inter-card gaps (or a slight overshoot) still land somewhere sensible.
                fun dateAtY(y: Float): String? {
                    val localY = y - listBounds.value.top
                    val items = listState.layoutInfo.visibleItemsInfo
                    if (items.isEmpty()) return null
                    val hit = items.firstOrNull { localY >= it.offset && localY < it.offset + it.size }
                        ?: items.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - localY) }
                    return hit?.key as? String
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
                        hoveredDate.value = dateAtY(e.y)
                    }
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        scrollSpeed.floatValue = 0f
                        hoveredDate.value = null
                        val e = event.toAndroidDragEvent()
                        val text = e.clipData?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString() ?: return false
                        val parts = text.split("|")
                        if (parts.size != 2) return false
                        val (entryId, fromDate) = parts
                        val toDate = dateAtY(e.y) ?: return false
                        if (fromDate == toDate) return false
                        viewModel.moveDish(entryId, fromDate, toDate)
                        return true
                    }
                    override fun onExited(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hoveredDate.value = null
                    }
                    override fun onEnded(event: DragAndDropEvent) {
                        scrollSpeed.floatValue = 0f; hoveredDate.value = null
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
                        isDropTarget = hoveredDate.value == day.date,
                        onAdd = { onPickRecipe(day.date) },
                        onSmartPick = { viewModel.reroll(day.date) },
                        onRemoveDish = removeDishWithUndo,
                        onToggleCooked = { planned -> viewModel.toggleCooked(planned, day.date) },
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
) {
    Column(Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(
                WeekSelection.CURRENT to stringResource(R.string.mealplan_week_current),
                WeekSelection.NEXT to stringResource(R.string.mealplan_week_next),
            )
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label) }
            }
        }
        // Spell out the actual Mon–Sun range so the user always knows which days the
        // toggle currently maps to — segmented control alone could be ambiguous mid-week.
        if (week.isNotEmpty()) {
            val first = runCatching { LocalDate.parse(week.first().date) }.getOrNull()
            val last = runCatching { LocalDate.parse(week.last().date) }.getOrNull()
            if (first != null && last != null) {
                val fmt = remember { DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMAN) }
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
    isDropTarget: Boolean,
    onAdd: () -> Unit,
    onSmartPick: () -> Unit,
    onRemoveDish: (String, PlannedRecipe) -> Unit,
    onToggleCooked: (PlannedRecipe) -> Unit,
    onOpenRecipe: (String) -> Unit,
) {
    val today = LocalDate.now().toString()
    val isToday = day.date == today
    // Past = the day has gone by; only then does "cooked yet?" make sense.
    val isPast = day.date < today

    // Highlighted while a dragged dish hovers this day (the list-level drop target tracks
    // which day is under the finger and passes it down here).
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDropTarget -> MaterialTheme.colorScheme.secondaryContainer
                isToday -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(day.label, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // One dish per day: offer "add" when empty, "remove" when set.
                    val existing = day.entries.firstOrNull()
                    if (existing != null) {
                        // Optional 1-tap "cooked" — only on today/past, where it can have happened.
                        if (isToday || isPast) {
                            // Same restaurant glyph + green as the recipe-detail "cooked" toggle.
                            TooltipIcon(
                                tooltip = stringResource(
                                    if (existing.cooked) R.string.mealplan_uncooked else R.string.mealplan_cooked,
                                ),
                                icon = Icons.Outlined.Restaurant,
                                onClick = { onToggleCooked(existing) },
                                tint = if (existing.cooked) MaterialTheme.colorScheme.secondary else null,
                            )
                        }
                        TooltipIcon(
                            tooltip = stringResource(R.string.mealplan_remove_dish),
                            icon = Icons.Outlined.DeleteOutline,
                            onClick = { onRemoveDish(day.date, existing) },
                        )
                    } else {
                        // Empty day: a manual "+" plus a one-tap smart pick that scores
                        // this single day against the rest of the week (neighbours, pantry,
                        // recency) and drops in the best fit immediately.
                        TooltipIcon(
                            tooltip = stringResource(R.string.mealplan_smart_pick),
                            icon = Icons.Outlined.AutoAwesome, // same "magic" icon as "Woche vorschlagen"
                            onClick = onSmartPick,
                        )
                        TooltipIcon(
                            tooltip = stringResource(R.string.mealplan_add_recipe),
                            icon = Icons.Outlined.Add,
                            onClick = onAdd,
                        )
                    }
                }
            }
            day.entries.forEach { planned ->
                // A past day that was never confirmed cooked is shown faded: it's over,
                // and the app makes no assumption about whether it actually happened.
                PlannedRow(planned, day.date, onOpenRecipe, faded = isPast && !planned.cooked)
            }
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
    onOpenRecipe: (String) -> Unit,
    faded: Boolean = false,
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
                        onTap = { onOpenRecipe(planned.recipeId) },
                        onLongPress = {
                            startTransfer(
                                DragAndDropTransferData(
                                    ClipData.newPlainText("dish", "${planned.entryId}|$fromDate"),
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
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer))
        }
        Column(Modifier.weight(1f)) {
            Text(planned.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Shopping pill ("Alles da" / "n fehlt") sits next to the "why?" help icon —
            // both relate to the same dish-vs-week question, so they belong in one row.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Once cooked, missing ingredients are moot — show only the "cooked" badge.
                if (planned.cooked) {
                    CookedBadge()
                } else {
                    AvailabilityBadge(missingCount = planned.missing, missingItems = planned.missingItems)
                }
                if (planned.reasons.isNotEmpty()) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.mealplan_reasons_why)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = { showWhy = true },
                            modifier = Modifier.size(28.dp),
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
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        if (filled) {
            FilledTonalIconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = tooltip) }
        } else {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(icon, contentDescription = tooltip, tint = tint ?: LocalContentColor.current)
            }
        }
    }
}
