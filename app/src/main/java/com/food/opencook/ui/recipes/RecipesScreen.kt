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

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.local.relation.RecipeListItem
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.LocalSnackbarHostState
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.components.RecipeCard
import com.food.opencook.ui.review.CategoryChips
import com.food.opencook.ui.review.MealTypeChips
import com.food.opencook.ui.scan.AddRecipeSheet
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.CookedFilter
import com.food.opencook.util.MealTypes
import com.food.opencook.util.RecipeCategories
import kotlinx.coroutines.launch

@Composable
fun RecipesScreen(
    onRecipeClick: (String) -> Unit,
    onOpenCamera: () -> Unit = {},
    onCreateManually: () -> Unit = {},
    onDiscover: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val baseUrl by viewModel.serverBaseUrl.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val cookbooks by viewModel.cookbooks.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    // Set when the user picks another order: the next (re-sorted) list starts at the top,
    // otherwise the grid would keep following whichever recipe happened to be first.
    var scrollToTop by remember { mutableStateOf(false) }
    LaunchedEffect(recipes) {
        if (scrollToTop) {
            gridState.scrollToItem(0)
            scrollToTop = false
        }
    }
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    var bulkEditing by remember { mutableStateOf(false) }
    var addingRecipe by remember { mutableStateOf(false) }
    val onAddRecipe = { addingRecipe = true }
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Back leaves selection mode first, before it leaves the screen.
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    total = recipes.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onEdit = { bulkEditing = true },
                    onDelete = { confirmDelete = true },
                )
            } else AppTopBar(
                // Count reflects what's shown: total when unfiltered, match count while searching/filtering.
                title = if (recipes.isNotEmpty()) {
                    stringResource(R.string.recipes_title_count, recipes.size)
                } else {
                    stringResource(R.string.recipes_title)
                },
                syncStatus = syncStatus,
                onSync = appBar::sync,
                actions = {
                    IconButton(onClick = onAddRecipe) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recipes_add))
                    }
                },
            )
        },
    ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.screen).padding(top = Spacing.sm)) {
        val activeFilterCount = filters.activeCount

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                // Filter entry point lives inside the search field — costs no space.
                // The badge shows how many filters are active so a filtered (possibly
                // empty-looking) list is never a mystery.
                IconButton(onClick = { showFilterSheet = true }) {
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) }
                        },
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.recipes_filter))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        // Only active filters appear here, each as a removable chip — always visible
        // what currently narrows the list, one tap to drop a criterion. The full
        // pick lists live in the sheet.
        if (activeFilterCount > 0) {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (filters.likedOnly) {
                    item(key = "filter-liked") {
                        ActiveFilterChip(
                            label = stringResource(R.string.recipes_filter_liked),
                            onRemove = { viewModel.setLikedOnly(false) },
                        )
                    }
                }
                if (filters.cookableOnly) {
                    item(key = "filter-cookable") {
                        ActiveFilterChip(
                            label = stringResource(R.string.recipes_filter_cookable),
                            onRemove = { viewModel.setCookableOnly(false) },
                        )
                    }
                }
                filters.cooked?.let { cooked ->
                    item(key = "filter-cooked") {
                        ActiveFilterChip(
                            label = stringResource(cookedFilterLabel(cooked)),
                            onRemove = { viewModel.toggleCooked(cooked) },
                        )
                    }
                }
                items(MealTypes.KEYS.filter { it in filters.mealTypes }, key = { "filter-meal-$it" }) { key ->
                    ActiveFilterChip(
                        label = stringResource(MealTypes.labelRes(key)),
                        onRemove = { viewModel.toggleMealType(key) },
                    )
                }
                items(RecipeCategories.KEYS.filter { it in filters.categories }, key = { "filter-cat-$it" }) { key ->
                    ActiveFilterChip(
                        label = stringResource(RecipeCategories.labelRes(key)),
                        onRemove = { viewModel.toggleCategory(key) },
                    )
                }
                items(cookbooks.filter { it in filters.cookbooks }, key = { "filter-cookbook-$it" }) { cookbook ->
                    ActiveFilterChip(
                        label = cookbook,
                        onRemove = { viewModel.toggleCookbook(cookbook) },
                    )
                }
            }
        }

        val filtering = query.isNotBlank() || activeFilterCount > 0
        if (recipes.isEmpty()) {
            EmptyState(
                icon = if (!filtering) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Search,
                title = stringResource(if (!filtering) R.string.recipes_empty_title else R.string.recipes_search_empty_title),
                message = stringResource(if (!filtering) R.string.recipes_empty_msg else R.string.recipes_search_empty_msg),
                actionLabel = if (!filtering) stringResource(R.string.recipes_add) else null,
                onAction = if (!filtering) onAddRecipe else null,
            )
        } else {
            // Tablet landscape has room for tiles: 1 column on a phone, 2 on a medium width,
            // 3 on a wide tablet. maxWidth measures the real content width (after the nav rail).
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cols = when {
                    maxWidth < 600.dp -> 1
                    maxWidth < 900.dp -> 2
                    else -> 3
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    state = gridState,
                    contentPadding = PaddingValues(vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    gridItems(recipes, key = { it.recipe.id }) { recipe ->
                        RecipeCard(
                            title = recipe.recipe.name ?: "—",
                            subtitle = listOfNotNull(recipe.recipe.recipeYield, recipe.recipe.cookbook).joinToString(" · ").ifBlank { null },
                            imageModel = imageModelFor(recipe.images, baseUrl),
                            liked = recipe.recipe.id in likedIds,
                            // While selecting, a tap picks instead of opening — the usual
                            // Android pattern, entered by a long-press on any card.
                            onClick = {
                                if (selecting) viewModel.toggleSelected(recipe.recipe.id)
                                else onRecipeClick(recipe.recipe.id)
                            },
                            onLongClick = { viewModel.toggleSelected(recipe.recipe.id) },
                            selected = if (selecting) recipe.recipe.id in selection else null,
                        )
                    }
                }
            }
        }
    }
    }

    AddRecipeSheet(
        show = addingRecipe,
        onDismiss = { addingRecipe = false },
        onOpenCamera = onOpenCamera,
        onCreateManually = onCreateManually,
        onDiscover = onDiscover,
        onOpenSettings = onOpenSettings,
    )

    if (bulkEditing) {
        val picked = remember { viewModel.selectedRecipes() }
        BulkEditSheet(
            picked = picked,
            cookbooks = cookbooks,
            onApply = { cookbook, category, add, remove ->
                bulkEditing = false
                viewModel.editSelection(cookbook, category, add, remove)
                val message = context.resources.getQuantityString(R.plurals.recipes_bulk_done, picked.size, picked.size)
                scope.launch { snackbarHostState.showSnackbar(message) }
            },
            onDismiss = { bulkEditing = false },
        )
    }

    if (confirmDelete) {
        val count = selection.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.recipes_delete_selected_title, count, count)) },
            text = { Text(pluralStringResource(R.plurals.recipes_delete_selected_text, count)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSelected()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = filters,
            cookbooks = cookbooks,
            sort = sort,
            onSort = {
                if (it != sort) {
                    scrollToTop = true
                    viewModel.setSort(it)
                }
            },
            onToggleMealType = viewModel::toggleMealType,
            onToggleCategory = viewModel::toggleCategory,
            onToggleCookbook = viewModel::toggleCookbook,
            onLikedOnly = viewModel::setLikedOnly,
            onCookableOnly = viewModel::setCookableOnly,
            onCooked = viewModel::toggleCooked,
            onClear = viewModel::clearFilters,
            onDismiss = { showFilterSheet = false },
        )
    }
}

/**
 * Replaces the top bar while recipes are selected (Material's contextual top bar): leave,
 * a tri-state "all" box with "n of m", edit and delete. On a raised surface
 * so the mode switch is visible, not only the changed title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    total: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val all = count == total
    val boxLabel = stringResource(if (all) R.string.recipes_select_none else R.string.recipes_select_all)
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Some → all; all → none, which also ends the selection.
                TriStateCheckbox(
                    state = if (all) ToggleableState.On else ToggleableState.Indeterminate,
                    onClick = if (all) onClose else onSelectAll,
                    modifier = Modifier.semantics { contentDescription = boxLabel },
                )
                Text(
                    stringResource(R.string.recipes_selected_of, count, total),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.recipes_selection_cancel))
            }
        },
        actions = {
            // Cookbook, meals and category have no icon anyone reads without a label, so
            // they share the app's one edit pencil and sit, labelled, in its sheet.
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.recipe_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.recipe_delete))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

/** One titled block of the bulk-edit sheet — heading styled like the filter sheet's. */
@Composable
private fun BulkSection(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        content()
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * Edit cookbook, meals and category of every picked recipe at once, with the editor's own
 * chips. Each starts from what the recipes share — or shows "mixed" where they differ —
 * and only what the user touches is applied; the rest stays per recipe.
 *
 * Title on top, Cancel/Apply pinned below the scrolling body, so Apply never scrolls out
 * of reach. The cookbook is one field with a suggestion menu: a household easily has thirty
 * cookbooks, and as chips they buried the rest of the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BulkEditSheet(
    picked: List<RecipeListItem>,
    cookbooks: List<String>,
    /** cookbook / category: null = untouched ("" clears the cookbook). */
    onApply: (cookbook: String?, category: String?, addMeals: Set<String>, removeMeals: Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Cookbook: prefilled when they agree. Touching the field while they differ is a
    // change even when it ends up empty — that is how "take them all out" is said.
    val currentBooks = picked.map { it.recipe.cookbook?.trim().orEmpty() }.distinct()
    val booksMixed = currentBooks.size > 1
    val initialBook = currentBooks.singleOrNull().orEmpty()
    var book by remember { mutableStateOf(initialBook) }
    var bookTouched by remember { mutableStateOf(false) }
    var bookMenu by remember { mutableStateOf(false) }
    val bookChange = book.trim().takeIf { bookTouched && (booksMixed || it != initialBook) }
    val bookSuggestions = cookbooks.filter { it.contains(book.trim(), ignoreCase = true) && it != book.trim() }

    // Meals: key → wanted on all (true) / none (false); absent = untouched. A meal only
    // some of them have shows the checkbox's "indeterminate" dash until it is tapped.
    val perRecipe = picked.map { MealTypes.fromStored(it.recipe.mealTypes).toSet() }
    val inAll = MealTypes.KEYS.filter { key -> perRecipe.all { key in it } }.toSet()
    val inSome = MealTypes.KEYS.filter { key -> perRecipe.any { key in it } }.toSet() - inAll
    var wanted by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    val shownMeals = MealTypes.KEYS.filter { wanted[it] ?: (it in inAll) }
    val addMeals = wanted.filterValues { it }.keys - inAll
    val removeMeals = wanted.filterValues { !it }.keys.filter { it in inAll || it in inSome }.toSet()

    // Category: the picked set replaces each recipe's set; nothing picked = "untouched".
    val currentCategories = picked.map { RecipeCategories.normalizeStored(it.recipe.category).orEmpty() }.distinct()
    val commonCategory = currentCategories.singleOrNull().orEmpty()
    var category by remember { mutableStateOf(commonCategory) }
    val categoryChange = category.takeIf { it.isNotEmpty() && it != commonCategory }

    val canApply = bookChange != null || categoryChange != null || addMeals.isNotEmpty() || removeMeals.isNotEmpty()
    val mixedLabel = stringResource(R.string.recipes_bulk_mixed)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            pluralStringResource(R.plurals.recipes_bulk_edit_title, picked.size, picked.size),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
        ) {
            BulkSection(title = stringResource(R.string.review_cookbook)) {
                ExposedDropdownMenuBox(
                    expanded = bookMenu && bookSuggestions.isNotEmpty(),
                    onExpandedChange = { bookMenu = it },
                ) {
                    OutlinedTextField(
                        value = book,
                        onValueChange = { book = it; bookTouched = true; bookMenu = true },
                        placeholder = {
                            Text(
                                if (booksMixed && !bookTouched) mixedLabel else stringResource(R.string.recipes_bulk_no_cookbook),
                            )
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                        trailingIcon = {
                            // Emptying is how the cookbook is removed, so it gets a button.
                            if (book.isNotEmpty()) {
                                IconButton(onClick = { book = ""; bookTouched = true }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.recipes_bulk_no_cookbook))
                                }
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookMenu)
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = bookMenu && bookSuggestions.isNotEmpty(),
                        onDismissRequest = { bookMenu = false },
                    ) {
                        bookSuggestions.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { book = name; bookTouched = true; bookMenu = false },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }

            BulkSection(
                title = stringResource(R.string.recipe_mealtypes_label),
                hint = stringResource(R.string.recipes_bulk_mealtypes_hint).takeIf { inSome.isNotEmpty() },
            ) {
                MealTypeChips(
                    selected = shownMeals,
                    onToggle = { key -> wanted = wanted + (key to (key !in shownMeals)) },
                    mixed = inSome.filterNot { it in wanted }.toSet(),
                    showLabel = false,
                )
            }

            BulkSection(
                title = stringResource(R.string.review_category),
                hint = mixedLabel.takeIf { currentCategories.size > 1 && categoryChange == null },
            ) {
                CategoryChips(current = category, onPick = { category = it }, showLabel = false)
            }
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.processing_cancel)) }
            Button(
                onClick = { onApply(bookChange, categoryChange, addMeals, removeMeals) },
                enabled = canApply,
                modifier = Modifier.padding(start = Spacing.sm),
            ) { Text(stringResource(R.string.recipes_bulk_apply)) }
        }
    }
}

/** Label for a cooking-status choice — shared by the sheet's chips and the active-filter row. */
@StringRes
private fun cookedFilterLabel(value: CookedFilter): Int = when (value) {
    CookedFilter.COOKED -> R.string.recipes_filter_cooked
    CookedFilter.NEVER -> R.string.recipes_filter_never_cooked
    CookedFilter.STALE -> R.string.recipes_filter_stale
}

/** An active sheet-filter in the chip row: label + ✕, one tap removes the criterion. */
@Composable
private fun ActiveFilterChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.recipes_filter_remove, label),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

/**
 * The filter sheet behind the search field's Tune icon: multi-select within a group,
 * AND between groups. Lives in a sheet (not a permanent chip row) so filtering costs
 * no screen space until it's wanted.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: RecipeFilters,
    cookbooks: List<String>,
    sort: RecipeSort,
    onSort: (RecipeSort) -> Unit,
    onToggleMealType: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleCookbook: (String) -> Unit,
    onLikedOnly: (Boolean) -> Unit,
    onCookableOnly: (Boolean) -> Unit,
    onCooked: (CookedFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.recipes_filter_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClear, enabled = filters.activeCount > 0) {
                    Text(stringResource(R.string.recipes_filter_reset))
                }
            }

            // Order first: it always applies, the filters below only when picked.
            Text(
                stringResource(R.string.recipes_sort),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(
                    RecipeSort.NEWEST to stringResource(R.string.recipes_sort_newest),
                    RecipeSort.NAME to stringResource(R.string.recipes_sort_name),
                )
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = sort == option,
                        onClick = { onSort(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(label) }
                }
            }

            Text(
                stringResource(R.string.recipe_mealtypes_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                MealTypes.KEYS.forEach { key ->
                    FilterChip(
                        selected = key in filters.mealTypes,
                        onClick = { onToggleMealType(key) },
                        label = { Text(stringResource(MealTypes.labelRes(key))) },
                    )
                }
            }

            Text(
                stringResource(R.string.review_category),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                RecipeCategories.KEYS.forEach { key ->
                    FilterChip(
                        selected = key in filters.categories,
                        onClick = { onToggleCategory(key) },
                        label = { Text(stringResource(RecipeCategories.labelRes(key))) },
                    )
                }
            }

            if (cookbooks.isNotEmpty()) {
                Text(
                    stringResource(R.string.review_cookbook),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    cookbooks.forEach { cookbook ->
                        FilterChip(
                            selected = cookbook in filters.cookbooks,
                            onClick = { onToggleCookbook(cookbook) },
                            label = { Text(cookbook) },
                        )
                    }
                }
            }

            // These two used to sit unlabelled at the foot of the sheet. They are states of
            // the dish rather than facets of it, so they get a heading of their own — which
            // is also what makes room for the cooking status below.
            Text(
                stringResource(R.string.recipes_filter_status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FilterChip(
                    selected = filters.likedOnly,
                    onClick = { onLikedOnly(!filters.likedOnly) },
                    leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.recipes_filter_liked)) },
                )
                // "What can I cook right now?" — everything the pantry covers end to end.
                // Staples don't count against it, so salt in the recipe doesn't disqualify a dish.
                FilterChip(
                    selected = filters.cookableOnly,
                    onClick = { onCookableOnly(!filters.cookableOnly) },
                    leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.recipes_filter_cookable)) },
                )
            }

            // Where the dish stands in the rotation. Single-select, because the three
            // contradict each other — "cooked before" and "never" cannot both be wanted.
            Text(
                stringResource(R.string.recipes_filter_cooked_group),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CookedFilter.entries.forEach { value ->
                    FilterChip(
                        selected = filters.cooked == value,
                        onClick = { onCooked(value) },
                        leadingIcon = if (value == CookedFilter.COOKED) {
                            { Icon(Icons.Outlined.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        label = { Text(stringResource(cookedFilterLabel(value))) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
