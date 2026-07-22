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
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.components.RecipeCard
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.MealTypes
import com.food.opencook.util.RecipeCategories

@Composable
fun RecipesScreen(
    onRecipeClick: (String) -> Unit,
    onAddRecipe: () -> Unit = {},
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val baseUrl by viewModel.serverBaseUrl.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val cookbooks by viewModel.cookbooks.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
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
                    contentPadding = PaddingValues(vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    gridItems(recipes, key = { it.recipe.id }) { recipe ->
                        RecipeCard(
                            title = recipe.recipe.name ?: "—",
                            subtitle = listOfNotNull(recipe.recipe.recipeYield, recipe.recipe.cookbook).joinToString(" · ").ifBlank { null },
                            imageModel = imageModelFor(recipe.images, baseUrl),
                            onClick = { onRecipeClick(recipe.recipe.id) },
                        )
                    }
                }
            }
        }
    }
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = filters,
            cookbooks = cookbooks,
            onToggleMealType = viewModel::toggleMealType,
            onToggleCategory = viewModel::toggleCategory,
            onToggleCookbook = viewModel::toggleCookbook,
            onLikedOnly = viewModel::setLikedOnly,
            onClear = viewModel::clearFilters,
            onDismiss = { showFilterSheet = false },
        )
    }
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
    onToggleMealType: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onToggleCookbook: (String) -> Unit,
    onLikedOnly: (Boolean) -> Unit,
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

            Spacer(Modifier.height(Spacing.md))
            FilterChip(
                selected = filters.likedOnly,
                onClick = { onLikedOnly(!filters.likedOnly) },
                leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.recipes_filter_liked)) },
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
