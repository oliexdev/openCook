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

package com.food.opencook.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.food.opencook.R
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.DateLabels
import com.food.opencook.util.DurationFormat
import com.food.opencook.util.Numbers
import com.food.opencook.util.RecipeShare
import java.time.LocalDate
import java.time.temporal.ChronoUnit


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val baseUrl by viewModel.serverBaseUrl.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val cooked by viewModel.cooked.collectAsStateWithLifecycle()
    val targetServings by viewModel.targetServings.collectAsStateWithLifecycle()

    // Keep the screen awake while a recipe is open — you cook straight from this view.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val addedMessage = stringResource(R.string.shopping_added)
    val exportChooserTitle = stringResource(R.string.recipe_export)
    val exportNotReadyMsg = stringResource(R.string.recipe_export_not_ready)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPlanSheet by remember { mutableStateOf(false) }
    val planAddedFormat = stringResource(R.string.recipe_plan_added)
    val planned by viewModel.plannedDishes.collectAsStateWithLifecycle()

    // Cooked-off-plan: today had a different dish planned → swap happens automatically; the
    // snackbar only informs (where the displaced dish went) and offers an undo.
    val lastSwap by viewModel.lastSwap.collectAsStateWithLifecycle()
    val movedTemplate = stringResource(R.string.recipe_cook_moved)
    val removedTemplate = stringResource(R.string.recipe_cook_removed)
    val undoLabel = stringResource(R.string.undo)
    val dayFmt = remember { DateLabels.weekdayDayMonth() }
    LaunchedEffect(lastSwap) {
        val s = lastSwap ?: return@LaunchedEffect
        val movedLabel = s.movedTo?.let { runCatching { LocalDate.parse(it).format(dayFmt) }.getOrNull() }
        val msg = if (movedLabel != null) movedTemplate.format(s.displacedName, movedLabel)
            else removedTemplate.format(s.displacedName)
        val res = snackbarHostState.showSnackbar(message = msg, actionLabel = undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
        if (res == SnackbarResult.ActionPerformed) viewModel.undoSwap(s) else viewModel.clearLastSwap()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.recipe_delete_confirm_title)) },
            text = { Text(stringResource(R.string.recipe_delete_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; viewModel.delete(onBack) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(recipe?.recipe?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(
                        onClick = {
                            val json = viewModel.exportJson()
                            if (json == null) {
                                scope.launch { snackbarHostState.showSnackbar(exportNotReadyMsg) }
                            } else {
                                val fileNameHint = recipe?.recipe?.name?.ifBlank { null } ?: recipeId
                                val intent = RecipeShare.shareIntent(context, json, fileNameHint)
                                context.startActivity(Intent.createChooser(intent, exportChooserTitle))
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.recipe_export))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.recipe_edit))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.recipe_delete))
                    }
                },
            )
        },
    ) { innerPadding ->
        val data = recipe
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val model = imageModelFor(data.images, baseUrl)
        val onAddToShopping: () -> Unit = {
            viewModel.addToShoppingList { scope.launch { snackbarHostState.showSnackbar(addedMessage) } }
        }
        val onPlan: () -> Unit = { showPlanSheet = true }
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            // Tablet landscape: ingredients (the reference you glance at) beside the steps (the
            // focus while cooking), each scrolling on its own — no more scroll-pingpong. Narrow
            // widths (phones, portrait) keep the familiar single scrolling column.
            if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Name lives in the top bar already, so the left pane starts with the image.
                        ImageHeader(model, liked, cooked)
                        RecipeMeta(data)
                        IngredientsSection(data, targetServings, viewModel::setServings)
                        // Tags are secondary while cooking → bottom of the reference column.
                        TagChips(data.recipe.tags)
                    }
                    VerticalDivider()
                    Column(
                        Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Quick actions sit above the steps (the cooking focus), reachable without
                        // scrolling through the ingredients on the left.
                        ActionButtons(data, cooked, liked, onAddToShopping, onPlan, viewModel::toggleCooked, viewModel::toggleLiked)
                        InstructionsSection(data)
                        NotesSection(data)
                        NutritionSection(data)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ImageHeader(model, liked, cooked)
                    Text(data.recipe.name ?: "—", style = MaterialTheme.typography.headlineSmall)
                    RecipeMeta(data)
                    TagChips(data.recipe.tags)
                    ActionButtons(data, cooked, liked, onAddToShopping, onPlan, viewModel::toggleCooked, viewModel::toggleLiked)
                    IngredientsSection(data, targetServings, viewModel::setServings)
                    InstructionsSection(data)
                    NotesSection(data)
                    NutritionSection(data)
                }
            }
        }
    }

    if (showPlanSheet) {
        AddToMealPlanSheet(
            weeks = viewModel.planWeekDates,
            planned = planned,
            onAssign = viewModel::assignToMealPlan,
            onReplace = viewModel::replaceOnMealPlan,
            onDismiss = { showPlanSheet = false },
            onAssigned = { dayLabel ->
                scope.launch { snackbarHostState.showSnackbar(planAddedFormat.format(dayLabel)) }
            },
        )
    }
}

/** The dish image (or a warm placeholder) with the liked/cooked status badges. */
@Composable
private fun ImageHeader(model: Any?, liked: Boolean, cooked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        if (liked) LikedBadge(Modifier.align(Alignment.TopStart))
        if (cooked) CookedRibbon(Modifier.align(Alignment.TopEnd))
    }
}

/** Shopping / plan / cooked / like quick actions. */
@Composable
private fun ActionButtons(
    data: RecipeWithDetails,
    cooked: Boolean,
    liked: Boolean,
    onAddToShopping: () -> Unit,
    onPlan: () -> Unit,
    onToggleCooked: () -> Unit,
    onToggleLiked: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (data.ingredients.isNotEmpty()) {
            // Neutral (not green) — "add to the shopping list".
            OutlinedIconButton(onClick = onAddToShopping) {
                Icon(Icons.Outlined.AddShoppingCart, contentDescription = stringResource(R.string.shopping_add_from_recipe))
            }
        }
        // Assign this recipe to a day in the current or next week.
        OutlinedIconButton(onClick = onPlan) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = stringResource(R.string.recipe_add_to_plan))
        }
        // Toggle "has been cooked" — green when confirmed (also shows as the image ribbon).
        if (cooked) {
            FilledTonalIconButton(
                onClick = onToggleCooked,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) { Icon(Icons.Outlined.Restaurant, contentDescription = stringResource(R.string.recipe_cooked_marked)) }
        } else {
            OutlinedIconButton(onClick = onToggleCooked) {
                Icon(Icons.Outlined.Restaurant, contentDescription = stringResource(R.string.recipe_cooked_mark))
            }
        }
        // Like toggle; heart stays red when liked.
        if (liked) {
            FilledTonalIconButton(
                onClick = onToggleLiked,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.recipe_like)) }
        } else {
            OutlinedIconButton(onClick = onToggleLiked) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = stringResource(R.string.recipe_like))
            }
        }
    }
}

/** Ingredients with the portions stepper that scales the displayed amounts. */
@Composable
private fun IngredientsSection(data: RecipeWithDetails, targetServings: Int?, onServings: (Int) -> Unit) {
    if (data.ingredients.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_ingredients), style = MaterialTheme.typography.titleMedium)

        // Portions stepper — scales the displayed amounts (only if servings is known).
        val baseServings = data.recipe.servings
        val effectiveServings = targetServings ?: baseServings
        if (baseServings != null && baseServings > 0 && effectiveServings != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onServings(effectiveServings - 1) }, enabled = effectiveServings > 1) { Text("−") }
                Text(
                    "$effectiveServings ${stringResource(R.string.settings_household_size_label)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = { onServings(effectiveServings + 1) }) { Text("+") }
            }
        }
        val factor = Numbers.scaleFor(baseServings, effectiveServings ?: (baseServings ?: 1))
        data.ingredients.sortedBy { it.position }.forEach { ing ->
            val q = Numbers.scaleQuantity(ing.quantity, factor)
            // Larger text: you read ingredients from an arm's length while cooking.
            Text("• " + Numbers.displayIngredient(q, ing.unit, ing.name), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Numbered preparation steps, set in a comfortable cook-from-screen size. */
@Composable
private fun InstructionsSection(data: RecipeWithDetails) {
    if (data.instructions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_instructions), style = MaterialTheme.typography.titleMedium)
        data.instructions.sortedBy { it.position }.forEachIndexed { i, step ->
            Text("${i + 1}. ${step.text}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NotesSection(data: RecipeWithDetails) {
    val notes = data.recipe.notes?.takeIf { it.isNotBlank() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_notes), style = MaterialTheme.typography.titleMedium)
        Text(notes, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NutritionSection(data: RecipeWithDetails) {
    val n = data.nutrition ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HorizontalDivider()
        Text(stringResource(R.string.review_nutrition), style = MaterialTheme.typography.titleMedium)
        n.basis?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        listOfNotNull(
            n.calories?.let { stringResource(R.string.nutrition_calories_value, it) },
            n.proteinContent?.let { stringResource(R.string.nutrition_protein_value, it) },
            n.fatContent?.let { stringResource(R.string.nutrition_fat_value, it) },
            n.carbohydrateContent?.let { stringResource(R.string.nutrition_carbs_value, it) },
        ).forEach { Text(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(tags: String?) {
    val list = tags?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (list.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { tag ->
            AssistChip(onClick = {}, label = { Text(tag) })
        }
    }
}

/**
 * Meta line under the title: servings (with a people icon), then the source cookbook,
 * then prep/cook times. Wraps to a second line on narrow screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeMeta(data: RecipeWithDetails) {
    val r = data.recipe
    val servings = r.recipeYield ?: r.servings?.let { stringResource(R.string.wizard_summary_servings, it.toString()) }
    val times = listOfNotNull(
        r.prepTime?.let { DurationFormat.toHuman(it) }?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.recipe_meta_prep, it) },
        r.cookTime?.let { DurationFormat.toHuman(it) }?.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.recipe_meta_cook, it) },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        servings?.let { MetaItem(Icons.Outlined.Group, it) }
        r.cookbook?.takeIf { it.isNotBlank() }?.let { MetaItem(Icons.AutoMirrored.Outlined.MenuBook, it) }
        times?.let { MetaItem(Icons.Outlined.Schedule, it) }
        r.lastCookedAt?.let { lastCookedLabel(it) }?.let {
            MetaItem(Icons.Outlined.Restaurant, "${stringResource(R.string.recipe_last_cooked_prefix)}: $it")
        }
    }
}

/** Human "last cooked" recency: today / yesterday / N days / N weeks ago. Null if unparseable. */
@Composable
private fun lastCookedLabel(iso: String): String? {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(date, LocalDate.now()).coerceAtLeast(0)
    return when {
        days == 0L -> stringResource(R.string.recipe_cooked_today)
        days == 1L -> stringResource(R.string.recipe_cooked_yesterday)
        days < 7L -> pluralStringResource(R.plurals.recipe_cooked_days_ago, days.toInt(), days.toInt())
        days < 14L -> stringResource(R.string.recipe_cooked_week_ago)
        else -> {
            val weeks = (days / 7L).toInt()
            pluralStringResource(R.plurals.recipe_cooked_weeks_ago, weeks, weeks)
        }
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Diagonal green corner banner shown on the image (top-right) once the recipe is cooked.
 *  The bar is centred on the corner diagonal (symmetric offset) and the label is centred
 *  within the bar, so "Gekocht" sits in the middle of the visible banner. */
@Composable
private fun CookedRibbon(modifier: Modifier = Modifier) {
    Box(modifier.size(130.dp).clipToBounds()) {
        Text(
            text = stringResource(R.string.recipe_cooked_badge),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 32.dp, y = (-32).dp)
                .rotate(45f)
                .width(200.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(vertical = 6.dp),
        )
    }
}

/** Small heart badge shown on the image (top-left) when the recipe is liked:
 *  a filled red heart on a light circle for contrast against the photo. */
@Composable
private fun LikedBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.padding(10.dp),
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.recipe_liked_label),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(7.dp).size(18.dp),
        )
    }
}
