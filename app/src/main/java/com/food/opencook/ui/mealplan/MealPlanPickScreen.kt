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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    // Ask the planner what it would put here. Computed once per cell, not per keystroke.
    LaunchedEffect(date, slot) { mealPlanViewModel.loadSuggestion(date, slot) }
    val suggestion by mealPlanViewModel.suggestion.collectAsStateWithLifecycle()

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
                // Three different nothings, and saying "no recipes yet" for all of them is
                // what makes the meal filter feel broken: the library may be full, just with
                // nothing marked as breakfast. Then the honest answer names the meal and
                // points at the chip that lifts the filter.
                val filteredOut = query.isBlank() && filters.mealTypes.isNotEmpty()
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = when {
                        filteredOut -> stringResource(
                            R.string.mealplan_slot_no_recipes,
                            stringResource(MealTypes.labelRes(slot)),
                        )
                        query.isBlank() -> stringResource(R.string.mealplan_no_recipes)
                        else -> stringResource(R.string.mealplan_search_empty_title)
                    },
                    message = when {
                        filteredOut -> stringResource(R.string.mealplan_pick_clear_filter)
                        query.isBlank() -> null
                        else -> stringResource(R.string.mealplan_search_empty_message)
                    },
                )
            } else {
                // The planner's proposal, only while the list is unsearched: once the user
                // starts typing they are looking for something specific, and a card that
                // ignores the query would read as a broken search rather than as an offer.
                val proposed = suggestion
                    ?.takeIf { query.isBlank() }
                    ?.let { s -> recipes.firstOrNull { it.recipe.id == s.recipeId } }
                // Shown once, at the top — repeating it further down would look like two
                // different recipes with the same name.
                val rest = recipes.filterNot { it.recipe.id == proposed?.recipe?.id }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (proposed != null) {
                        item(key = "suggestion") {
                            // Lifted out of the list on three counts: the wand that also sits
                            // on "suggest the next 7 days", a tinted ground and outline, and —
                            // the one that actually earns the space — the planner's own top
                            // reason. That turns the card from an assertion into an argument,
                            // and it is information nothing else on this screen carries.
                            val context = LocalContext.current
                            val why = suggestion?.reasons
                                ?.maxByOrNull { it.weight }
                                ?.let { MealPlanReasons.text(context, it) }
                                ?.takeIf { it.isNotBlank() }

                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.mealplan_suggestion),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                RecipeCard(
                                    title = proposed.recipe.name ?: "—",
                                    subtitle = listOfNotNull(proposed.recipe.recipeYield, proposed.recipe.cookbook)
                                        .joinToString(" · ").ifBlank { null },
                                    imageModel = imageModelFor(proposed.images, baseUrl),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                    onClick = {
                                        // Taking the planner's own pick carries its reasons, so
                                        // the dish can still explain itself on the plan.
                                        mealPlanViewModel.choose(
                                            date, slot, proposed.recipe.id,
                                            suggestion?.reasons.orEmpty(),
                                        )
                                        onBack()
                                    },
                                )
                                if (why != null) {
                                    Text(
                                        why,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider(
                                    Modifier.padding(top = Spacing.sm),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                    items(rest, key = { it.recipe.id }) { recipe ->
                        RecipeCard(
                            title = recipe.recipe.name ?: "—",
                            subtitle = listOfNotNull(recipe.recipe.recipeYield, recipe.recipe.cookbook).joinToString(" · ").ifBlank { null },
                            imageModel = imageModelFor(recipe.images, baseUrl),
                            onClick = {
                                mealPlanViewModel.choose(date, slot, recipe.recipe.id)
                                onBack()
                            },
                        )
                    }
                }
            }
        }
    }
}
