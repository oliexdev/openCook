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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.data.settings.RecipeViewMode
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.components.RecipeCard
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.RecipesLayout

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
    val selectedCookbook by viewModel.selectedCookbook.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
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
                    IconButton(
                        onClick = {
                            viewModel.setViewMode(if (viewMode == RecipeViewMode.LIST) RecipeViewMode.GRID else RecipeViewMode.LIST)
                        },
                    ) {
                        // Icon shows the mode a tap switches TO, not the current one.
                        if (viewMode == RecipeViewMode.LIST) {
                            Icon(Icons.Outlined.GridView, contentDescription = stringResource(R.string.recipes_view_grid))
                        } else {
                            Icon(Icons.Outlined.ViewAgenda, contentDescription = stringResource(R.string.recipes_view_list))
                        }
                    }
                    IconButton(onClick = onAddRecipe) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recipes_add))
                    }
                },
            )
        },
    ) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.screen).padding(top = Spacing.sm)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )

        if (cookbooks.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    FilterChip(
                        selected = selectedCookbook == null,
                        onClick = { viewModel.selectCookbook(null) },
                        label = { Text(stringResource(R.string.recipes_filter_all)) },
                    )
                }
                items(cookbooks) { cookbook ->
                    FilterChip(
                        selected = selectedCookbook == cookbook,
                        onClick = { viewModel.selectCookbook(if (selectedCookbook == cookbook) null else cookbook) },
                        label = { Text(cookbook) },
                    )
                }
            }
        }

        if (recipes.isEmpty()) {
            EmptyState(
                icon = if (query.isBlank()) Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Search,
                title = stringResource(if (query.isBlank()) R.string.recipes_empty_title else R.string.recipes_search_empty_title),
                message = stringResource(if (query.isBlank()) R.string.recipes_empty_msg else R.string.recipes_search_empty_msg),
                actionLabel = if (query.isBlank()) stringResource(R.string.recipes_add) else null,
                onAction = if (query.isBlank()) onAddRecipe else null,
            )
        } else {
            // Tablet landscape has room for tiles: columns scale with width and view mode
            // (see RecipesLayout). maxWidth measures the real content width (after the nav rail).
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cols = RecipesLayout.columnsFor(maxWidth.value, viewMode)
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
                            squareImage = viewMode == RecipeViewMode.GRID,
                        )
                    }
                }
            }
        }
    }
    }
}
