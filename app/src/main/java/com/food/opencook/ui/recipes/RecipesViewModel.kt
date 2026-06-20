package com.food.opencook.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.local.relation.RecipeWithDetails
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
import javax.inject.Inject

@HiltViewModel
class RecipesViewModel @Inject constructor(
    repository: RecipeRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val all: StateFlow<List<RecipeWithDetails>> =
        repository.observeRecipes()
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
        settings.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setQuery(value: String) { _query.value = value }
    fun selectCookbook(value: String?) { _cookbook.value = value }
}
