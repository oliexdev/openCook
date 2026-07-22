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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.util.MealTypes
import com.food.opencook.util.RecipeCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Filter-sheet state: multi-select within a group, AND between groups. Empty = off. */
data class RecipeFilters(
    val mealTypes: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val cookbooks: Set<String> = emptySet(),
    val likedOnly: Boolean = false,
) {
    /** Number of active filters — drives the badge on the search field's filter icon. */
    val activeCount: Int get() =
        mealTypes.size + categories.size + cookbooks.size + (if (likedOnly) 1 else 0)
}

/**
 * The list's filter predicate, extracted as a pure function so the search behavior
 * is unit-testable without Android. [categoryLabel] resolves the localized category
 * label (needs a Context at the call site, so it's injected as a lambda).
 */
internal object RecipeSearchFilter {
    fun matches(
        item: RecipeWithDetails,
        query: String,
        filters: RecipeFilters,
        likedIds: Set<String>,
        categoryLabel: (String?) -> String,
    ): Boolean {
        val q = query.trim()
        // Full text covers everything a user would type from memory: the dish name,
        // a tag, an ingredient ("what can I cook with leek?"), the category word,
        // or the cookbook the recipe came from.
        val matchesQuery = q.isBlank() ||
            item.recipe.name?.contains(q, ignoreCase = true) == true ||
            item.recipe.tags?.split("\n")?.any { it.contains(q, ignoreCase = true) } == true ||
            item.ingredients.any { it.name.contains(q, ignoreCase = true) } ||
            item.recipe.cookbook?.contains(q, ignoreCase = true) == true ||
            (item.recipe.category != null && categoryLabel(item.recipe.category).contains(q, ignoreCase = true))
        val matchesMeal = filters.mealTypes.isEmpty() ||
            MealTypes.fromStored(item.recipe.mealTypes).any { it in filters.mealTypes }
        val matchesCategory = filters.categories.isEmpty() ||
            RecipeCategories.normalizeKey(item.recipe.category) in filters.categories
        val matchesCookbook = filters.cookbooks.isEmpty() || item.recipe.cookbook in filters.cookbooks
        val matchesLiked = !filters.likedOnly || item.recipe.id in likedIds
        return matchesQuery && matchesMeal && matchesCategory && matchesCookbook && matchesLiked
    }
}

@HiltViewModel
class RecipesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: RecipeRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val all: StateFlow<List<RecipeWithDetails>> =
        repository.observeRecipes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(RecipeFilters())
    val filters: StateFlow<RecipeFilters> = _filters.asStateFlow()

    /** Distinct cookbook names present, for the filter chips. */
    val cookbooks: StateFlow<List<String>> = all
        .map { list -> list.mapNotNull { it.recipe.cookbook?.takeIf(String::isNotBlank) }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val likedIds: StateFlow<Set<String>> =
        repository.observeLikedRecipeIds()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val recipes: StateFlow<List<RecipeWithDetails>> =
        combine(all, _query, _filters, likedIds) { list, q, filters, liked ->
            list.filter { item ->
                RecipeSearchFilter.matches(item, q, filters, liked) { key ->
                    RecipeCategories.displayLabel(context, key)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverBaseUrl: StateFlow<String?> =
        settings.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setQuery(value: String) { _query.value = value }

    fun toggleMealType(key: String) = _filters.update {
        it.copy(mealTypes = if (key in it.mealTypes) it.mealTypes - key else it.mealTypes + key)
    }

    fun toggleCookbook(name: String) = _filters.update {
        it.copy(cookbooks = if (name in it.cookbooks) it.cookbooks - name else it.cookbooks + name)
    }

    /** The pick screen's "Alle" chip: back to every cookbook. */
    fun clearCookbooks() = _filters.update { it.copy(cookbooks = emptySet()) }

    fun toggleCategory(key: String) = _filters.update {
        it.copy(categories = if (key in it.categories) it.categories - key else it.categories + key)
    }

    fun setLikedOnly(value: Boolean) = _filters.update { it.copy(likedOnly = value) }

    fun clearFilters() { _filters.value = RecipeFilters() }
}
