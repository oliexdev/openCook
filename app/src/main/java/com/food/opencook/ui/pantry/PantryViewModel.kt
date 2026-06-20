package com.food.opencook.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.SuggestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val repository: PantryRepository,
    private val suggestionRepository: SuggestionRepository,
) : ViewModel() {

    val items: StateFlow<List<PantryItemEntity>> =
        repository.observeItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var suggestionPool: List<String> = emptyList()
    init { viewModelScope.launch { suggestionPool = suggestionRepository.pool() } }
    fun suggestions(query: String): List<String> = SuggestionRepository.filter(suggestionPool, query)

    fun add(name: String) = viewModelScope.launch { repository.addItem(name) }
    fun delete(id: String) = viewModelScope.launch { repository.deleteItem(id) }

    /** Re-add a just-deleted pantry item (Undo). */
    fun restore(item: PantryItemEntity) = viewModelScope.launch { repository.addItem(item.name) }
}
