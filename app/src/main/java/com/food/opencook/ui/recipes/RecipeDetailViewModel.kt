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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.export.ExportFormat
import com.food.opencook.data.export.RecipeExporter
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.ui.mealplan.MealPlanSlots
import com.food.opencook.ui.navigation.Routes
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.MealTypes
import com.food.opencook.util.PlanWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** A dish already planned on a given day, with the bits the picker sheet needs to render. */
data class PlannedDish(val name: String, val imageModel: Any?)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
    private val pantryRepository: PantryRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val settings: SettingsRepository,
    private val exporter: RecipeExporter,
) : ViewModel() {

    private val recipeId: String = checkNotNull(savedStateHandle[Routes.ARG_RECIPE_ID])

    /** Delete the recipe (emits a tombstone so the deletion syncs), then leave. */
    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repository.deleteRecipe(recipeId)
        onDeleted()
    }

    /** Name suggestion for the SAF "create document" dialog, e.g. "Omas-Pfannkuchen.md". */
    fun suggestedFileName(format: ExportFormat): String =
        recipe.value?.let { exporter.fileName(it, format) } ?: "recipe.${format.extension}"

    /** Write this recipe into the document the user just created via the file picker. */
    fun export(target: Uri, format: ExportFormat, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val details = repository.getRecipeOnce(recipeId)
        val ok = details != null && runCatching { exporter.export(details, target, format) }.isSuccess
        onResult(ok)
    }

    /** Copy this recipe's ingredients onto the shopping list, skipping pantry staples. */
    fun addToShoppingList(onAdded: () -> Unit) = viewModelScope.launch {
        recipe.value?.let { shoppingRepository.addFromRecipe(it) }
        onAdded()
    }

    val recipe: StateFlow<RecipeWithDetails?> =
        repository.observeRecipe(recipeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val serverBaseUrl: StateFlow<String?> =
        settings.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** How often the household has cooked this dish. `lastCookedAt` only remembers the most
     *  recent day; the count comes from the plan's confirmed-cooked entries. */
    val cookedCount: StateFlow<Int> =
        mealPlanRepository.observeCookedCount(recipeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Target portions for the scaling stepper; null = show the recipe's own servings. */
    private val _targetServings = MutableStateFlow<Int?>(null)
    val targetServings: StateFlow<Int?> = _targetServings.asStateFlow()

    fun setServings(value: Int) { _targetServings.value = value.coerceAtLeast(1) }

    /** This device's own "liked" state for the heart toggle. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liked: StateFlow<Boolean> =
        flow { emit(settings.ensureNodeId()) }
            .flatMapLatest { node -> repository.observeLike(recipeId, node) }
            .map { it?.liked == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleLiked() = viewModelScope.launch {
        repository.setLiked(recipeId, settings.ensureNodeId(), !liked.value)
    }

    /**
     * The plan row this screen was opened from, or null when it was reached from the recipe
     * list. It decides *which* meal "mark as cooked" confirms: opening Monday's dish from the
     * planner and marking it cooked has to tick Monday, not today.
     */
    private val planEntryId: String? =
        savedStateHandle.get<String>(Routes.ARG_PLAN_ENTRY)?.takeIf { it.isNotBlank() }

    /**
     * Whether this dish counts as cooked right now. Coming from the planner that means "this
     * planned meal is confirmed"; otherwise it's the per-day mark on the recipe (cooked today),
     * not a sticky "ever cooked" flag, so the same dish can be re-marked next time.
     */
    val cooked: StateFlow<Boolean> =
        if (planEntryId != null) {
            mealPlanRepository.observeEntry(planEntryId).map { it?.cookedAt != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        } else {
            repository.observeRecipe(recipeId)
                .map { it?.recipe?.lastCookedAt == LocalDate.now().toString() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        }

    /** Set when this screen was opened from a plan row on a day other than today — the
     *  "cooked" label must then not claim it happened *today*. */
    val confirmsOtherDay: StateFlow<Boolean> =
        if (planEntryId == null) MutableStateFlow(false)
        else mealPlanRepository.observeEntry(planEntryId)
            .map { it != null && it.date != LocalDate.now().toString() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The prior last-cooked date, captured when marking cooked, so an undo/untap can restore it. */
    private var previousCooked: String? = null

    /** One planned entry relocated by the ripple-shift (kept so the swap can be undone). */
    data class SwapMove(
        val entryId: String,
        val recipeId: String,
        val fromDate: String,
        val toDate: String,
        /** The meal the ripple runs in — dishes only ever displace their own kind. */
        val slot: String,
    )
    /** Enough state to inform what happened and undo an auto-applied swap. */
    data class SwapUndo(
        /** Entries shifted forward — reversed on undo. */
        val moves: List<SwapMove>,
        /** (recipeId, date, slot) entries that were removed — re-created on undo. */
        val readd: List<Triple<String, String, String>>,
        val cookedEntryId: String,
        val displacedName: String,
        /** Day the displaced dish moved to (tomorrow), or null if it was removed from today. */
        val movedTo: String?,
        /** Recipe's prior last-cooked date, restored on undo (un-marks today's cook). */
        val previousCooked: String?,
    )

    private val _lastSwap = MutableStateFlow<SwapUndo?>(null)
    val lastSwap: StateFlow<SwapUndo?> = _lastSwap.asStateFlow()

    fun clearLastSwap() { _lastSwap.value = null }

    /**
     * Mark this recipe cooked **today** (stamps `lastCookedAt` + consumes the pantry). Re-markable
     * any later day. When marking, also reconcile today's plan: if today held a *different*,
     * non-pinned dish, swap today → this dish (cooked) and reschedule/drop the other (a snackbar
     * informs + undoes). Tapping it again the same day un-marks today's cook (restores the prior date).
     */
    fun toggleCooked() = viewModelScope.launch {
        val today = LocalDate.now().toString()
        // Opened from a plan row: confirm exactly that meal, on its own day. Only today's
        // plan can be *rearranged* (swapping in a dish you actually cooked, rippling the
        // displaced one forward) — for Monday or for next Friday there is nothing sensible
        // to reschedule, so this just records that the meal happened.
        planEntryId?.let { entryId ->
            val entry = mealPlanRepository.getForEntry(entryId) ?: return@let
            if (entry.date != today) {
                val nowCooked = !cooked.value
                mealPlanRepository.setCooked(entryId, nowCooked)
                // The recipe's own last-cooked date only moves forward: confirming last
                // Monday must not overwrite a more recent cook.
                if (nowCooked && (repository.getRecipeOnce(recipeId)?.recipe?.lastCookedAt ?: "") < entry.date) {
                    repository.markCookedOn(recipeId, entry.date)
                }
                return@launch
            }
        }
        if (cooked.value) {
            // Already cooked today → un-mark, restoring whatever the last-cooked date was before.
            repository.restoreLastCookedAt(recipeId, previousCooked)
            return@launch
        }
        // Remember the prior date (for undo), then stamp today + consume the pantry.
        val prev = repository.getRecipeOnce(recipeId)?.recipe?.lastCookedAt
        previousCooked = prev
        repository.markCookedOn(recipeId, today)
        val plannedSlots = settings.plannedMealsOnce()
        val entries = mealPlanRepository.getForDates(listOf(today))
        // Which of today's meals is this? If the dish is on the plan at all, that entry wins
        // regardless of the clock (you cooked what you planned, even if it's late). Otherwise
        // the meal the current time belongs to is the one being replaced.
        val slot = entries.firstOrNull { it.recipeId == recipeId }
            ?.let { MealPlanSlots.resolve(it.slot, plannedSlots) }
            ?: MealPlanSlots.currentSlot(plannedSlots, LocalTime.now().hour)
        val planned = entries.firstOrNull { MealPlanSlots.resolve(it.slot, plannedSlots) == slot }
            ?: return@launch
        when {
            // Cooked exactly what was planned → just confirm that entry.
            planned.recipeId == recipeId -> mealPlanRepository.setCooked(planned.id, true)
            // User pinned today's dish — leave the plan alone, just track that this was cooked.
            planned.pinned -> Unit
            else -> swapTodayWith(planned.id, planned.recipeId, today, slot, prev)
        }
    }

    /**
     * Today's plan becomes this dish (cooked). The displaced dish is kept only if its ingredients
     * are bought / on the list / fully in the pantry — then the plan **ripples forward by a day**:
     * displaced → tomorrow, tomorrow's dish → the day after, and so on until an already-free day
     * absorbs the shift (a fully-booked window pushes the last dish off the end). Not procured →
     * the displaced dish is simply removed from today.
     */
    private suspend fun swapTodayWith(
        displacedEntryId: String,
        displacedRecipeId: String,
        today: String,
        slot: String,
        prevCooked: String?,
    ) {
        val name = repository.getRecipeOnce(displacedRecipeId)?.recipe?.name ?: "Gericht"
        val procured = shoppingRepository.hasItemsFor(displacedRecipeId, today) || fullyInPantry(displacedRecipeId)
        val plannedSlots = settings.plannedMealsOnce()

        val moves = mutableListOf<SwapMove>()
        val readd = mutableListOf<Triple<String, String, String>>()
        var movedTo: String? = null

        if (procured) {
            val todayDate = LocalDate.parse(today)
            val future = planWeekDates.flatten().map(LocalDate::parse).filter { it.isAfter(todayDate) }.sorted()
            // The ripple stays inside this meal: a displaced dinner pushes the following
            // dinners along, it never lands on top of somebody's breakfast.
            val occupant = mealPlanRepository.getForDates(future.map(LocalDate::toString))
                .filter { MealPlanSlots.resolve(it.slot, plannedSlots) == slot }
                .associateBy { it.date }
            var carryId = displacedEntryId
            var carryRecipe = displacedRecipeId
            var carryFrom = today
            var landed = false
            for (day in future) {
                val dayStr = day.toString()
                moves += SwapMove(carryId, carryRecipe, carryFrom, dayStr, slot)
                val occ = occupant[dayStr]
                if (occ == null) { landed = true; break }
                carryId = occ.id; carryRecipe = occ.recipeId; carryFrom = occ.date
            }
            if (moves.isEmpty()) {
                // No day left to move into → just drop the displaced dish.
                mealPlanRepository.deleteEntry(displacedEntryId)
                readd += Triple(displacedRecipeId, today, slot)
            } else {
                movedTo = moves.first().toDate // the displaced dish lands on tomorrow
                if (!landed) {
                    // Window fully booked → the last dish ripples off the end; drop it.
                    mealPlanRepository.deleteEntry(carryId)
                    readd += Triple(carryRecipe, carryFrom, slot)
                }
                moves.forEach { m ->
                    mealPlanRepository.moveEntry(m.entryId, m.toDate, m.slot)
                    shoppingRepository.moveSource(m.recipeId, m.fromDate, m.toDate)
                }
            }
        } else {
            mealPlanRepository.deleteEntry(displacedEntryId)
            readd += Triple(displacedRecipeId, today, slot)
        }

        val cookedEntryId = mealPlanRepository.addCookedEntry(today, recipeId, slot)
        _lastSwap.value = SwapUndo(moves, readd, cookedEntryId, name, movedTo, prevCooked)
    }

    fun undoSwap(undo: SwapUndo) = viewModelScope.launch {
        mealPlanRepository.deleteEntry(undo.cookedEntryId)
        undo.moves.forEach { m ->
            mealPlanRepository.moveEntry(m.entryId, m.fromDate, m.slot)
            shoppingRepository.moveSource(m.recipeId, m.toDate, m.fromDate)
        }
        undo.readd.forEach { (rid, date, slot) -> mealPlanRepository.addEntry(date, rid, slot) }
        // Also un-mark today's cook, back to whatever the recipe's last-cooked date was before.
        repository.restoreLastCookedAt(recipeId, undo.previousCooked)
        _lastSwap.value = null
    }

    /** All of a recipe's ingredients are covered by the pantry — same notion as the "Alles da" badge. */
    private suspend fun fullyInPantry(rid: String): Boolean {
        val r = repository.getRecipeOnce(rid) ?: return false
        val pantry = pantryRepository.stockedNames()
        val names = r.ingredients.map { it.name.trim() }.filter { it.isNotEmpty() }
        return names.isNotEmpty() && names.all { IngredientMatch.containsLike(pantry, it) }
    }

    // --- Add to meal plan ---

    /** Rolling: today … today+13, grouped by calendar week, as ISO strings. Rolling rather
     *  than two fixed Mon–Sun pages, to match the planner — and because offering to put a
     *  recipe on a day that has already passed never made sense. */
    val planWeekDates: List<List<String>> =
        PlanWindow.byWeek((0L..13L).map { LocalDate.now().plusDays(it) })
            .map { group -> group.days.map(LocalDate::toString) }

    /** Which meals the household plans — the sheet offers a slot choice only when >1. */
    val plannedMeals: StateFlow<List<String>> = settings.plannedMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanSlots.DEFAULT_PLANNED)

    /** The meals this recipe is marked for — pre-selects the slot in the sheet, so adding a
     *  cake lands on "Snack" rather than on dinner. */
    val recipeMealTypes: StateFlow<List<String>> = repository.observeRecipe(recipeId)
        .map { MealTypes.fromStored(it?.recipe?.mealTypes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealTypes.DEFAULT)

    /** "date|slot" → the currently planned dish (name + thumbnail), if any. */
    val plannedDishes: StateFlow<Map<String, PlannedDish>> =
        combine(
            mealPlanRepository.observeForDates(planWeekDates.flatten()),
            repository.observeRecipes(),
            settings.serverUrl,
            settings.plannedMeals,
        ) { entries, recipes, baseUrl, plannedSlots ->
            val byId = recipes.associateBy { it.recipe.id }
            entries.associate { entry ->
                val r = byId[entry.recipeId]
                cellKey(entry.date, MealPlanSlots.resolve(entry.slot, plannedSlots)) to PlannedDish(
                    name = r?.recipe?.name ?: "Rezept",
                    imageModel = imageModelFor(r?.images.orEmpty(), baseUrl),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Add the current recipe to a cell as a fresh entry (used when the cell is empty). */
    fun assignToMealPlan(date: String, slot: String, onDone: () -> Unit) = viewModelScope.launch {
        mealPlanRepository.addEntry(date, recipeId, slot)
        onDone()
    }

    /** Replace whatever is planned in that cell with the current recipe (used when occupied). */
    fun replaceOnMealPlan(date: String, slot: String, onDone: () -> Unit) = viewModelScope.launch {
        mealPlanRepository.replaceCell(date, slot, recipeId, settings.plannedMealsOnce())
        onDone()
    }

    companion object {
        /** Key for the "what's planned where" map — a plan cell is a day *and* a meal. */
        fun cellKey(date: String, slot: String) = "$date|$slot"
    }
}
