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

package com.food.opencook.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.ui.recipes.imageModelFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** A planned meal as the Home screen shows it, with how many ingredients are still missing. */
data class HomeMeal(
    val entryId: String,
    val date: String,
    val label: String,
    val recipeId: String,
    val name: String,
    val subtitle: String?,
    val imageModel: Any?,
    val missing: Int,
    val missingItems: List<String> = emptyList(),
    /** True if this day's dish was confirmed cooked via the 1-tap toggle. */
    val cooked: Boolean = false,
)

data class HomeUiState(
    val today: List<HomeMeal> = emptyList(),
    val week: List<HomeMeal> = emptyList(),
    val recipeCount: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
    pantryRepository: PantryRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val weekDates: List<String> = (0L..6L).map { today.plusDays(it).toString() }
    private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())

    val uiState: StateFlow<HomeUiState> =
        combine(
            mealPlanRepository.observeForDates(weekDates),
            recipeRepository.observeRecipes(),
            pantryRepository.observeItems(),
            settings.serverUrl,
        ) { entries, recipes, pantry, baseUrl ->
            val byId = recipes.associateBy { it.recipe.id }
            val pantryNames = pantry.map { it.name.lowercase().trim() }.toSet()

            val meals = entries.mapNotNull { entry ->
                val recipe = byId[entry.recipeId] ?: return@mapNotNull null
                val missingItems = recipe.ingredients.mapNotNull { ing ->
                    ing.name.trim().takeIf { it.isNotEmpty() && it.lowercase() !in pantryNames }
                }
                HomeMeal(
                    entryId = entry.id,
                    date = entry.date,
                    label = labelFor(entry.date),
                    recipeId = entry.recipeId,
                    name = recipe.recipe.name ?: "Rezept",
                    subtitle = recipe.recipe.servings?.let { "$it Portionen" },
                    imageModel = imageModelFor(recipe.images, baseUrl),
                    missing = missingItems.size,
                    missingItems = missingItems,
                    cooked = entry.cookedAt != null,
                )
            }
            HomeUiState(
                today = meals.filter { it.date == today.toString() },
                week = meals.filter { it.date != today.toString() }.sortedBy { it.date },
                recipeCount = recipes.size,
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 1-tap "we cooked today's dish" — same effect as the meal-planner toggle: flips the
     *  entry's cooked flag and, when newly cooked, advances last-cooked + consumes the pantry. */
    fun toggleCooked(meal: HomeMeal) = viewModelScope.launch {
        val nowCooked = !meal.cooked
        mealPlanRepository.setCooked(meal.entryId, nowCooked)
        if (nowCooked) recipeRepository.markCookedOn(meal.recipeId, meal.date)
    }

    private fun labelFor(dateIso: String): String = when (dateIso) {
        today.toString() -> "Heute"
        today.plusDays(1).toString() -> "Morgen"
        else -> runCatching { LocalDate.parse(dateIso).format(weekdayFmt) }.getOrDefault(dateIso)
    }
}
