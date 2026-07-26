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

package com.food.opencook.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.GroceryOverrideRepository
import com.food.opencook.repository.IngredientLinkRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.repository.SuggestionRepository
import com.food.opencook.util.GroceryCategory
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.IngredientStaples
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A shopping line plus the resolved names of every dish that needs it (for the label). */
data class ShoppingRowUi(
    val item: ShoppingItemEntity,
    val recipeNames: List<String> = emptyList(),
)

/**
 * A recipe ingredient that was skipped because the pantry (apparently) covers it — shown in
 * the collapsible "N im Vorrat übersprungen" chip. [pantryName] is the stock item that
 * covered it, so "brauch ich doch" can teach the exact distinction. Staples never appear
 * here (they're a permanent, non-recoverable exclusion).
 */
data class SkippedItemUi(
    val itemId: String,
    val text: String,
    val pantryName: String,
)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val recipeRepository: RecipeRepository,
    private val pantryRepository: PantryRepository,
    private val suggestionRepository: SuggestionRepository,
    private val overrideRepository: GroceryOverrideRepository,
    private val ingredientLinkRepository: IngredientLinkRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** One-time swipe peek-hint gate for the shopping list. */
    val swipeHintSeen: StateFlow<Boolean> =
        settings.swipeHintSeenShopping.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun markSwipeHintSeen() = viewModelScope.launch { settings.setSwipeHintSeenShopping() }

    /** Learned "name → aisle" corrections; beats the keyword heuristic when grouping. */
    val overrides: StateFlow<Map<String, GroceryCategory>> =
        overrideRepository.observeOverrides()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** A dish was dragged into another aisle group: teach the household the lesson.
     *  The lists re-group reactively via [overrides] — nothing on the item changes. */
    fun recategorize(name: String, target: GroceryCategory) = viewModelScope.launch {
        overrideRepository.learn(name, target)
    }

    /**
     * Live list, with a reactive filter: a recipe-sourced item disappears from view when it's
     * a **staple** (always assumed on hand — never on the list unless added by hand) or its
     * name is **covered by the pantry**. **Manual entries always show** — adding something by
     * hand wins over "already in stock" (the [ShoppingItemEntity.manual] flag, latched on even
     * for consolidated manual+recipe lines). The DB entity of a hidden line stays around so
     * removing the pantry item un-hides it and it keeps syncing.
     *
     * Each row also carries the names of every dish that needs it (resolved from
     * [ShoppingItemEntity.sourceRecipeIds]) for the "needed for …" label.
     */
    val items: StateFlow<List<ShoppingRowUi>> =
        combine(
            repository.observeItems(),
            pantryRepository.observeItems(),
            recipeRepository.observeRecipes(),
        ) { all, pantry, recipes ->
            val names = recipes.associate { it.recipe.id to (it.recipe.name ?: "") }
            val pantryNames = pantry.map { it.name.lowercase().trim() }.toSet()
            val visible = all.filterNot { item ->
                !item.manual &&
                    (IngredientStaples.isStaple(item.text) || IngredientMatch.containsLike(pantryNames, item.text))
            }
            visible.map { item ->
                val recipeNames = item.sourceRecipeIds
                    ?.split(',')
                    ?.mapNotNull { id -> names[id]?.takeIf { it.isNotBlank() } }
                    .orEmpty()
                ShoppingRowUi(item, recipeNames)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The recipe items hidden **because the pantry covers them** (not staples) — the
     * "N im Vorrat übersprungen" chip. Each carries the covering pantry name so "brauch ich
     * doch" ([keepAnyway]) teaches the exact distinction and the item returns to [items].
     */
    val skippedItems: StateFlow<List<SkippedItemUi>> =
        combine(
            repository.observeItems(),
            pantryRepository.observeItems(),
        ) { all, pantry ->
            if (pantry.isEmpty()) return@combine emptyList()
            val pantryNames = pantry.map { it.name.lowercase().trim() }
            all.mapNotNull { item ->
                if (item.manual || IngredientStaples.isStaple(item.text)) return@mapNotNull null
                val coverer = pantryNames.firstOrNull { IngredientMatch.covers(it, item.text) }
                coverer?.let { SkippedItemUi(item.id, item.text, it) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when the list has items and every one is checked off — drives the "all bought" banner. */
    val allChecked: StateFlow<Boolean> =
        items.map { it.isNotEmpty() && it.all { row -> row.item.checked } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var suggestionPool: List<String> = emptyList()
    init { viewModelScope.launch { suggestionPool = suggestionRepository.pool() } }
    fun suggestions(query: String): List<String> = SuggestionRepository.filter(suggestionPool, query)

    fun add(text: String) = viewModelScope.launch { repository.addItem(text, manual = true) }
    fun setChecked(id: String, checked: Boolean) = viewModelScope.launch { repository.setChecked(id, checked) }
    fun delete(id: String) = viewModelScope.launch { repository.deleteItem(id) }
    /** Finish the shop: checked = bought → into the pantry, then off the list. */
    fun clearChecked() = viewModelScope.launch { repository.checkoutChecked() }
    fun clearAll() = viewModelScope.launch { repository.clearAll() }

    /** Re-add a just-deleted item (Undo). Checked state isn't restored. */
    fun restore(item: ShoppingItemEntity) = viewModelScope.launch {
        repository.addItem(item.text, item.quantity, item.unit, item.sourceRecipeId, item.sourceDate, item.manual)
    }

    /**
     * "Schon zu Hause" / "Hab ich schon": move the item off the list and into the pantry.
     * Delete from the list **first**, then add to the pantry — otherwise the item is briefly
     * both on the list and covered by the freshly-added pantry entry, flashing it into the
     * "N im Vorrat übersprungen" chip for a frame.
     */
    fun markAlreadyAtHome(item: ShoppingItemEntity) = viewModelScope.launch {
        repository.deleteItem(item.id)
        pantryRepository.addItem(item.text)
    }

    /**
     * "Brauch ich doch": a pantry item wrongly covered this recipe ingredient. Teach the
     * household that the two are not the same product — the item leaves the skip chip and
     * returns to the visible list, and stays there on future lists.
     */
    fun keepAnyway(skipped: SkippedItemUi) = viewModelScope.launch {
        ingredientLinkRepository.learnDistinct(skipped.text, skipped.pantryName)
    }
}
