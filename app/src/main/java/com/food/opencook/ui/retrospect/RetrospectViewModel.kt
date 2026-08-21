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

package com.food.opencook.ui.retrospect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.ui.recipes.imageModelFor
import com.food.opencook.util.DateLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One meal in the list: the dish, its photo, and the day it was cooked ("Sa 16."). */
data class RetrospectRow(
    val recipeId: String,
    val name: String,
    val imageModel: Any?,
    val dayLabel: String,
)

/** One month's worth of rows, under a "August 2026 · 14 Gerichte" header. */
data class RetrospectMonth(
    val key: String,
    val label: String,
    val count: Int,
    val rows: List<RetrospectRow>,
)

data class RetrospectUiState(
    val loading: Boolean = true,
    val months: List<RetrospectMonth> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && months.isEmpty()
}

@HiltViewModel
class RetrospectViewModel @Inject constructor(
    mealPlanRepository: MealPlanRepository,
    recipeRepository: RecipeRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // Formatted here rather than in the composables: the list can run to hundreds of rows, and
    // re-deriving a locale pattern per row per recomposition would be wasteful.
    private val dayFormat = DateLabels.weekdayDay()
    private val monthFormat = DateLabels.monthYear()

    val state: StateFlow<RetrospectUiState> = combine(
        mealPlanRepository.observeAllCooked(),
        recipeRepository.observeRecipes(),
        settingsRepository.serverUrl,
    ) { cooked, recipes, baseUrl ->
        val images = recipes.associate { it.recipe.id to imageModelFor(it.images, baseUrl) }
        val months = Retrospective.byMonth(cooked, recipes.map { it.recipe }).map { group ->
            RetrospectMonth(
                key = group.month.toString(),
                label = group.month.atDay(1).format(monthFormat),
                count = group.count,
                rows = group.meals.map { meal ->
                    RetrospectRow(
                        recipeId = meal.recipeId,
                        name = meal.name,
                        imageModel = images[meal.recipeId],
                        dayLabel = meal.date.format(dayFormat),
                    )
                },
            )
        }
        RetrospectUiState(loading = false, months = months)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RetrospectUiState())
}
