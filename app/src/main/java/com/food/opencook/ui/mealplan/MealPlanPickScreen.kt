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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.components.RecipeCard
import com.food.opencook.ui.recipes.RecipesViewModel
import com.food.opencook.ui.recipes.imageModelFor
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.MealTypes

/**
 * Pick a recipe for one cell of the plan ([date] + [slot]) — the full recipe list with
 * search and cookbook filters (reuses [RecipesViewModel]); tapping a recipe assigns it and
 * returns. Adding goes through a fresh [MealPlanViewModel]; it writes to the repository, so
 * the plan screen updates via its own observed flow.
 *
 * The list opens pre-filtered to recipes marked for this meal — the user came here from the
 * breakfast row, so breakfasts are what they want to see. The filter shows up as a normal,
 * removable chip rather than as a hidden rule, so everything is one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanPickScreen(
    date: String,
    slot: String,
    onBack: () -> Unit,
    recipesViewModel: RecipesViewModel = hiltViewModel(),
    mealPlanViewModel: MealPlanViewModel = hiltViewModel(),
) {
    val recipes by recipesViewModel.recipes.collectAsStateWithLifecycle()
    val baseUrl by recipesViewModel.serverBaseUrl.collectAsStateWithLifecycle()
    val query by recipesViewModel.query.collectAsStateWithLifecycle()
    val cookbooks by recipesViewModel.cookbooks.collectAsStateWithLifecycle()
    val filters by recipesViewModel.filters.collectAsStateWithLifecycle()

    // Seed the meal filter once per visit; afterwards it's the user's to change.
    LaunchedEffect(slot) { recipesViewModel.setMealTypeFilter(setOf(slot)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mealplan_pick_recipe)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Spacing.screen).padding(top = Spacing.sm)) {
            OutlinedTextField(
                value = query,
                onValueChange = recipesViewModel::setQuery,
                placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyRow(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // The active meal filter, first and removable — "showing breakfasts, tap to
                // see everything". Without it the short list would look like missing recipes.
                items(MealTypes.KEYS.filter { it in filters.mealTypes }) { key ->
                    InputChip(
                        selected = true,
                        onClick = { recipesViewModel.setMealTypeFilter(emptySet()) },
                        label = { Text(stringResource(MealTypes.labelRes(key))) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.recipes_filter_all),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            if (cookbooks.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item {
                        FilterChip(
                            selected = filters.cookbooks.isEmpty(),
                            onClick = { recipesViewModel.clearCookbooks() },
                            label = { Text(stringResource(R.string.recipes_filter_all)) },
                        )
                    }
                    items(cookbooks) { cookbook ->
                        FilterChip(
                            selected = cookbook in filters.cookbooks,
                            onClick = { recipesViewModel.toggleCookbook(cookbook) },
                            label = { Text(cookbook) },
                        )
                    }
                }
            }

            if (recipes.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = if (query.isBlank()) stringResource(R.string.mealplan_no_recipes) else stringResource(R.string.mealplan_search_empty_title),
                    message = if (query.isBlank()) null else stringResource(R.string.mealplan_search_empty_message),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(recipes, key = { it.recipe.id }) { recipe ->
                        RecipeCard(
                            title = recipe.recipe.name ?: "—",
                            subtitle = listOfNotNull(recipe.recipe.recipeYield, recipe.recipe.cookbook).joinToString(" · ").ifBlank { null },
                            imageModel = imageModelFor(recipe.images, baseUrl),
                            onClick = {
                                mealPlanViewModel.addRecipe(date, slot, recipe.recipe.id)
                                onBack()
                            },
                        )
                    }
                }
            }
        }
    }
}
