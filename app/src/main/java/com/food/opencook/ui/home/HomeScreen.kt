package com.food.opencook.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.food.opencook.R
import com.food.opencook.ui.AppBarViewModel
import com.food.opencook.ui.components.AppTopBar
import com.food.opencook.ui.components.AvailabilityBadge
import com.food.opencook.ui.components.CookedBadge
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.components.RecipeCard
import com.food.opencook.ui.components.SectionHeader
import com.food.opencook.ui.theme.Spacing

/** "Heute" dashboard: today's meal with ingredient availability, quick actions, week glance. */
@Composable
fun HomeScreen(
    onOpenRecipes: () -> Unit = {},
    onAddRecipe: () -> Unit = {},
    onOpenRecipe: (String) -> Unit = {},
    onOpenPlan: () -> Unit = {},
    onOpenShopping: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appBar: AppBarViewModel = hiltViewModel()
    val syncStatus by appBar.status.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(stringResource(R.string.home_title), syncStatus, appBar::sync) }) { innerPadding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (state.recipeCount == 0) {
            // Keep the content within the Scaffold's insets (top bar + status bar / camera
            // cutout); without this the empty state would draw up under the cutout.
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.home_welcome_title),
                    message = stringResource(R.string.home_welcome_msg),
                    actionLabel = stringResource(R.string.home_add_first),
                    onAction = onAddRecipe,
                )
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
        // Today's meal(s)
        if (state.today.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.home_today_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.home_today_empty_msg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilledTonalButton(onClick = onOpenPlan) { Text(stringResource(R.string.home_plan_week)) }
                        TextButton(onClick = onOpenRecipes) { Text(stringResource(R.string.home_quick_recipes)) }
                    }
                }
            }
        } else {
            state.today.forEach { meal ->
                Box {
                    RecipeCard(
                        title = meal.name,
                        subtitle = meal.subtitle,
                        imageModel = meal.imageModel,
                        onClick = { onOpenRecipe(meal.recipeId) },
                        imageHeight = 180,
                    )
                    AvailabilityBadge(
                        missingCount = meal.missing,
                        missingItems = meal.missingItems,
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                    )
                    if (meal.cooked) {
                        CookedBadge(Modifier.align(Alignment.TopStart).padding(Spacing.sm))
                    }
                    // 1-tap "we cooked this" right on today's dish — green when confirmed.
                    val cookedDesc = stringResource(
                        if (meal.cooked) R.string.recipe_cooked_marked else R.string.recipe_cooked_mark,
                    )
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleCooked(meal) },
                        colors = if (meal.cooked) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors()
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm),
                    ) { Icon(Icons.Outlined.Restaurant, contentDescription = cookedDesc) }
                }
            }
        }

        // Quick actions
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            QuickAction(stringResource(R.string.home_quick_shopping), Icons.Outlined.ShoppingCart, Modifier.weight(1f), onOpenShopping)
            QuickAction(stringResource(R.string.home_quick_plan), Icons.Outlined.CalendarMonth, Modifier.weight(1f), onOpenPlan)
            QuickAction(stringResource(R.string.home_quick_recipes), Icons.Outlined.MenuBook, Modifier.weight(1f), onOpenRecipes)
        }

        // This week
        SectionHeader(
            title = stringResource(R.string.home_week_section),
            trailing = { TextButton(onClick = onOpenPlan) { Text(stringResource(R.string.home_quick_plan)) } },
        )
        if (state.week.isEmpty()) {
            Text(
                stringResource(R.string.home_week_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.week.forEach { meal -> WeekRow(meal, onClick = { onOpenRecipe(meal.recipeId) }) }
        }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun WeekRow(meal: HomeMeal, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                meal.label.take(2).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(36.dp),
            )
            Text(
                meal.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AvailabilityBadge(missingCount = meal.missing, missingItems = meal.missingItems)
        }
    }
}
