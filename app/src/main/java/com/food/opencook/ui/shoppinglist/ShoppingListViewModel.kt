package com.food.opencook.ui.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.repository.SuggestionRepository
import com.food.opencook.ui.mealplan.MealPlanner
import com.food.opencook.util.IngredientMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A recipe the user could cook instead, from ingredients already on hand. */
data class RecipeSuggestion(val id: String, val name: String)

/** A shopping line plus the resolved names of every dish that needs it (for the label). */
data class ShoppingRowUi(
    val item: ShoppingItemEntity,
    val recipeNames: List<String> = emptyList(),
)

/**
 * State of the "ingredient not found" dialog. First the three choices; picking
 * "replace the dish" computes [suggestions] (then [searched] is true).
 */
data class NotFoundUi(
    val item: ShoppingItemEntity,
    val canReplace: Boolean,
    val searched: Boolean = false,
    val suggestions: List<RecipeSuggestion> = emptyList(),
)

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val recipeRepository: RecipeRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val pantryRepository: PantryRepository,
    private val suggestionRepository: SuggestionRepository,
) : ViewModel() {

    /**
     * Live list, with a reactive pantry filter: a recipe-sourced item whose name is
     * covered by the pantry disappears from view, but **manual entries always show** —
     * adding something by hand wins over "already in stock" (the [ShoppingItemEntity.manual]
     * flag, latched on even for consolidated manual+recipe lines). The DB entity of a
     * hidden line stays around so removing the pantry item un-hides it and it keeps syncing.
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
            val visible =
                if (pantry.isEmpty()) all
                else {
                    val pantryNames = pantry.map { it.name.lowercase().trim() }.toSet()
                    all.filterNot { item -> !item.manual && IngredientMatch.containsLike(pantryNames, item.text) }
                }
            visible.map { item ->
                val recipeNames = item.sourceRecipeIds
                    ?.split(',')
                    ?.mapNotNull { id -> names[id]?.takeIf { it.isNotBlank() } }
                    .orEmpty()
                ShoppingRowUi(item, recipeNames)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when the list has items and every one is checked off — drives the "all bought" banner. */
    val allChecked: StateFlow<Boolean> =
        items.map { it.isNotEmpty() && it.all { row -> row.item.checked } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var suggestionPool: List<String> = emptyList()
    init { viewModelScope.launch { suggestionPool = suggestionRepository.pool() } }
    fun suggestions(query: String): List<String> = SuggestionRepository.filter(suggestionPool, query)

    private val _notFound = MutableStateFlow<NotFoundUi?>(null)
    val notFound: StateFlow<NotFoundUi?> = _notFound.asStateFlow()

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

    /** "Schon zu Hause": move the item off the list and into the pantry. */
    fun markAlreadyAtHome(item: ShoppingItemEntity) = viewModelScope.launch {
        pantryRepository.addItem(item.text)
        repository.deleteItem(item.id)
    }

    // --- "not found" flow ---

    fun openNotFound(item: ShoppingItemEntity) {
        _notFound.value = NotFoundUi(
            item = item,
            canReplace = item.sourceRecipeId != null && item.sourceDate != null,
        )
    }

    fun dismissNotFound() {
        _notFound.value = null
    }

    /**
     * "Selbst abwandeln": user solves the missing-ingredient problem on their own
     * (substitute, skip, …) — mark the item as done so it stays visible (struck
     * through) but doesn't pretend to still need buying. Dismisses the dialog.
     */
    fun adaptItem(itemId: String) = viewModelScope.launch {
        repository.setChecked(itemId, true)
        _notFound.value = null
    }

    /** Case 2: suggest dishes cookable from the pantry + remaining list (minus the missing item). */
    fun findAlternatives() = viewModelScope.launch {
        val current = _notFound.value ?: return@launch
        val missing = current.item.text.lowercase().trim()
        val pantry = pantryRepository.stockedNames()
        val onList = items.value.filter { !it.item.checked }.map { it.item.text.lowercase().trim() }.toSet()
        val available = (pantry + onList) - missing

        val suggestions = MealPlanner.cookableFrom(available, recipeRepository.getAllRecipesOnce())
            .filter { recipe ->
                recipe.recipe.id != current.item.sourceRecipeId &&
                    recipe.ingredients.none { it.name.lowercase().trim() == missing }
            }
            .take(6)
            .map { RecipeSuggestion(it.recipe.id, it.recipe.name ?: "Rezept") }

        _notFound.update { it?.copy(searched = true, suggestions = suggestions) }
    }

    /** Swap the planned dish for [recipeId] and refresh that day's shopping contribution. */
    fun replaceWith(recipeId: String) = viewModelScope.launch {
        val current = _notFound.value ?: return@launch
        val oldRecipeId = current.item.sourceRecipeId ?: return@launch
        val date = current.item.sourceDate ?: return@launch
        val newRecipe = recipeRepository.getRecipeOnce(recipeId) ?: return@launch

        mealPlanRepository.replaceDay(date, recipeId)
        repository.replaceMealContribution(oldRecipeId, date, newRecipe, pantryRepository.stockedNames())
        _notFound.value = null
    }
}
