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
