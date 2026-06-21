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

package com.food.opencook.ui.barcode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.ProductLookupRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.repository.SuggestionRepository
import com.food.opencook.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a scanned product is added. */
enum class BarcodeTarget { SHOPPING, PANTRY }

data class BarcodeUiState(
    val barcode: String? = null,   // null = still scanning
    val looking: Boolean = false,
    val found: Boolean = false,    // a name came back from the lookup
    val name: String = "",         // editable, prefilled when found
)

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lookup: ProductLookupRepository,
    private val shoppingRepository: ShoppingRepository,
    private val pantryRepository: PantryRepository,
    private val suggestionRepository: SuggestionRepository,
) : ViewModel() {

    private val target: BarcodeTarget =
        if (savedStateHandle.get<String>(Routes.ARG_BARCODE_TARGET) == "pantry") BarcodeTarget.PANTRY
        else BarcodeTarget.SHOPPING

    private val _state = MutableStateFlow(BarcodeUiState())
    val state: StateFlow<BarcodeUiState> = _state.asStateFlow()

    private var pool: List<String> = emptyList()
    init { viewModelScope.launch { pool = suggestionRepository.pool() } }
    fun suggestions(query: String): List<String> = SuggestionRepository.filter(pool, query)

    /** Called by the analyzer on the first decoded barcode. */
    fun onScanned(ean: String) {
        if (_state.value.barcode != null) return // already captured one
        _state.update { it.copy(barcode = ean, looking = true) }
        viewModelScope.launch {
            val info = lookup.lookup(ean)
            _state.update { it.copy(looking = false, found = info != null, name = info?.name ?: "") }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    /** Resume scanning (user dismissed the confirm without adding). */
    fun rescan() = _state.update { BarcodeUiState() }

    fun confirm(onDone: () -> Unit) {
        val name = _state.value.name.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            when (target) {
                BarcodeTarget.SHOPPING -> shoppingRepository.addItem(name)
                BarcodeTarget.PANTRY -> pantryRepository.addItem(name)
            }
            onDone()
        }
    }
}
