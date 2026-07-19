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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.settings.RecipeViewMode
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipesViewModel @Inject constructor(
    recipeRepository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val all: StateFlow<List<RecipeWithDetails>> =
        recipeRepository.observeRecipes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** null = all cookbooks. */
    private val _cookbook = MutableStateFlow<String?>(null)
    val selectedCookbook: StateFlow<String?> = _cookbook.asStateFlow()

    /** Distinct cookbook names present, for the filter chips. */
    val cookbooks: StateFlow<List<String>> = all
        .map { list -> list.mapNotNull { it.recipe.cookbook?.takeIf(String::isNotBlank) }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recipes: StateFlow<List<RecipeWithDetails>> =
        combine(all, _query, _cookbook) { list, q, cookbook ->
            list.filter { item ->
                val matchesQuery = q.isBlank() ||
                    item.recipe.name?.contains(q, ignoreCase = true) == true ||
                    item.recipe.tags?.split("\n")?.any { it.contains(q, ignoreCase = true) } == true
                matchesQuery && (cookbook == null || item.recipe.cookbook == cookbook)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverBaseUrl: StateFlow<String?> =
        settingsRepository.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Recipe list layout (list vs. album grid); per-device, see [SettingsRepository.recipeViewMode]. */
    val viewMode: StateFlow<RecipeViewMode> =
        settingsRepository.recipeViewMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeViewMode.LIST)

    fun setViewMode(mode: RecipeViewMode) = viewModelScope.launch { settingsRepository.setRecipeViewMode(mode) }

    fun setQuery(value: String) { _query.value = value }
    fun selectCookbook(value: String?) { _cookbook.value = value }
}
