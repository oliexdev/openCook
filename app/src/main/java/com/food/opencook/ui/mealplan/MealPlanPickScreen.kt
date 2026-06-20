package com.food.opencook.ui.mealplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.components.RecipeCard
import com.food.opencook.ui.recipes.RecipesViewModel
import com.food.opencook.ui.recipes.imageModelFor
import com.food.opencook.ui.theme.Spacing

/**
 * Pick a recipe for a meal-plan [date] — the full recipe list with search and
 * cookbook filters (reuses [RecipesViewModel]); tapping a recipe assigns it and
 * returns. Adding goes through a fresh [MealPlanViewModel]; it writes to the
 * repository, so the plan screen updates via its own observed flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanPickScreen(
    date: String,
    onBack: () -> Unit,
    recipesViewModel: RecipesViewModel = hiltViewModel(),
    mealPlanViewModel: MealPlanViewModel = hiltViewModel(),
) {
    val recipes by recipesViewModel.recipes.collectAsStateWithLifecycle()
    val baseUrl by recipesViewModel.serverBaseUrl.collectAsStateWithLifecycle()
    val query by recipesViewModel.query.collectAsStateWithLifecycle()
    val cookbooks by recipesViewModel.cookbooks.collectAsStateWithLifecycle()
    val selectedCookbook by recipesViewModel.selectedCookbook.collectAsStateWithLifecycle()

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

            if (cookbooks.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item {
                        FilterChip(
                            selected = selectedCookbook == null,
                            onClick = { recipesViewModel.selectCookbook(null) },
                            label = { Text(stringResource(R.string.recipes_filter_all)) },
                        )
                    }
                    items(cookbooks) { cookbook ->
                        FilterChip(
                            selected = selectedCookbook == cookbook,
                            onClick = { recipesViewModel.selectCookbook(if (selectedCookbook == cookbook) null else cookbook) },
                            label = { Text(cookbook) },
                        )
                    }
                }
            }

            if (recipes.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = if (query.isBlank()) stringResource(R.string.mealplan_no_recipes) else "Nichts gefunden",
                    message = if (query.isBlank()) null else "Versuch einen anderen Suchbegriff.",
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
                                mealPlanViewModel.addRecipe(date, recipe.recipe.id)
                                onBack()
                            },
                        )
                    }
                }
            }
        }
    }
}
