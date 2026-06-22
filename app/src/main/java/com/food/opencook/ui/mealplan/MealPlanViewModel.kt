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

package com.food.opencook.ui.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.util.DateLabels
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.WeekDates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Which 7-day window the user is currently looking at. We expose exactly two —
 *  the current week (still being cooked) and the next week (being planned). */
enum class WeekSelection { CURRENT, NEXT }

data class PlannedRecipe(
    val entryId: String,
    val recipeId: String,
    val name: String,
    val pinned: Boolean,
    val imageModel: Any? = null,
    val missing: Int = 0,
    val missingItems: List<String> = emptyList(),
    /** Per-factor score breakdown that produced this pick — empty if the entry was
     *  added manually (not generated) or the app was restarted since last generate. */
    val reasons: List<MealPlanner.ReasonContribution> = emptyList(),
    /** True if this day's dish was confirmed cooked via the optional 1-tap. */
    val cooked: Boolean = false,
)
data class DayPlan(val date: String, val label: String, val entries: List<PlannedRecipe>)
data class RecipeOption(val id: String, val name: String)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanViewModel @Inject constructor(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
    private val pantryRepository: PantryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val labelFormat = DateLabels.weekdayDayMonth()

    private val _selectedWeek = MutableStateFlow(WeekSelection.CURRENT)
    val selectedWeek: StateFlow<WeekSelection> = _selectedWeek.asStateFlow()

    fun selectWeek(selection: WeekSelection) {
        _selectedWeek.value = selection
    }

    /** Always Monday–Sunday; selection offsets by full weeks. */
    private fun daysFor(selection: WeekSelection): List<LocalDate> =
        WeekDates.weekOf(weekOffset = if (selection == WeekSelection.NEXT) 1 else 0)

    private fun currentDays(): List<LocalDate> = daysFor(_selectedWeek.value)
    private fun currentDateKeys(): List<String> = currentDays().map(LocalDate::toString)

    val week: StateFlow<List<DayPlan>> = _selectedWeek
        .flatMapLatest { selection ->
            val days = daysFor(selection)
            val dateKeys = days.map(LocalDate::toString)
            combine(
                mealPlanRepository.observeForDates(dateKeys),
                recipeRepository.observeRecipes(),
                pantryRepository.observeItems(),
                settingsRepository.serverUrl,
            ) { entries, recipes, pantry, baseUrl ->
                val byId = recipes.associateBy { it.recipe.id }
                val pantryNames = pantry.map { it.name.lowercase().trim() }.toSet()
                days.map { day ->
                    val key = day.toString()
                    DayPlan(
                        date = key,
                        label = day.format(labelFormat),
                        entries = entries.filter { it.date == key }.map { entry ->
                            val recipe = byId[entry.recipeId]
                            val missingItems = recipe?.ingredients?.mapNotNull { ing ->
                                ing.name.trim().takeIf { it.isNotEmpty() && !IngredientMatch.containsLike(pantryNames, it) }
                            }.orEmpty()
                            PlannedRecipe(
                                entryId = entry.id,
                                recipeId = entry.recipeId,
                                name = recipe?.recipe?.name ?: "Rezept",
                                pinned = entry.pinned,
                                imageModel = com.food.opencook.ui.recipes.imageModelFor(recipe?.images.orEmpty(), baseUrl),
                                missing = missingItems.size,
                                missingItems = missingItems,
                                // Reasons travel on the entity via reasonsJson — sync, restart-safe.
                                reasons = mealPlanRepository.decodeReasons(entry.reasonsJson),
                                cooked = entry.cookedAt != null,
                            )
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while a week is being generated / shopping list built — drives a loading UI. */
    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** Recipes to choose from when assigning to a day. */
    val recipeOptions: StateFlow<List<RecipeOption>> =
        recipeRepository.observeRecipes()
            .map { list -> list.map { RecipeOption(it.recipe.id, it.recipe.name ?: "—") } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addRecipe(date: String, recipeId: String) = viewModelScope.launch {
        mealPlanRepository.addEntry(date, recipeId)
    }

    fun remove(entryId: String) = viewModelScope.launch { mealPlanRepository.deleteEntry(entryId) }

    /**
     * Drag-and-drop a planned dish onto another day. On an empty target the dish
     * just moves; on an occupied target the two days swap their dishes (the model is
     * one dish per day). Shopping-list provenance follows each dish via [moveSource],
     * so already-listed ingredients keep pointing at the day they're cooked.
     */
    fun moveDish(entryId: String, fromDate: String, toDate: String) = viewModelScope.launch {
        if (fromDate == toDate) return@launch
        val entries = mealPlanRepository.getForDates(listOf(fromDate, toDate))
        val dragged = entries.firstOrNull { it.id == entryId } ?: return@launch
        // Swap partner: an existing dish on the target day (excluding the dragged one).
        val target = entries.firstOrNull { it.date == toDate && it.id != entryId }

        mealPlanRepository.moveEntry(dragged.id, toDate)
        shoppingRepository.moveSource(dragged.recipeId, fromDate, toDate)
        if (target != null) {
            mealPlanRepository.moveEntry(target.id, fromDate)
            shoppingRepository.moveSource(target.recipeId, toDate, fromDate)
        }
    }

    fun togglePin(entry: PlannedRecipe) = viewModelScope.launch {
        mealPlanRepository.setPinned(entry.entryId, !entry.pinned)
    }

    /** Optional 1-tap "we cooked this". Also advances the recipe's last-cooked date so
     *  the planner mildly favours variety. Doing nothing stays valid — this only refines. */
    fun toggleCooked(entry: PlannedRecipe, date: String) = viewModelScope.launch {
        val nowCooked = !entry.cooked
        mealPlanRepository.setCooked(entry.entryId, nowCooked)
        if (nowCooked) recipeRepository.markCookedOn(entry.recipeId, date)
    }

    /**
     * Self-healing carry-forward. A planned day that has passed without a "cooked"
     * confirmation rolls onto the next free day — but only if its ingredients were
     * procured (bought, or fully covered by the pantry); otherwise the food isn't on
     * hand and the entry just stays put (faded). Idempotent: safe to call on every open;
     * cross-device conflicts resolve via HLC last-write-wins on the `date` field.
     */
    fun reconcilePastDays() = viewModelScope.launch {
        val today = LocalDate.now()
        val past = mealPlanRepository
            .getForDateRange(today.minusDays(LOOKBACK_DAYS).toString(), today.minusDays(1).toString())
            .filter { it.cookedAt == null && !it.pinned }
        if (past.isEmpty()) return@launch

        val windowKeys = (0..MealPlanReconciler.DEFAULT_WINDOW_DAYS).map { today.plusDays(it).toString() }
        val occupied = mealPlanRepository.getForDates(windowKeys).map { LocalDate.parse(it.date) }.toSet()
        val pantry = pantryRepository.stockedNames()

        val candidates = past.map { entry ->
            val procured = shoppingRepository.isProcured(entry.recipeId, entry.date) ||
                fullyInPantry(entry.recipeId, pantry)
            MealPlanReconciler.PastEntry(entry.id, LocalDate.parse(entry.date), procured)
        }
        MealPlanReconciler.reconcile(candidates, occupied, emptySet(), today)
            .forEach { mealPlanRepository.moveEntry(it.entryId, it.toDate.toString()) }
    }

    /** All of a recipe's ingredients are covered by the pantry — same notion as the
     *  "Alles da" badge, so "procured" matches what the user already sees. */
    private suspend fun fullyInPantry(recipeId: String, pantry: Set<String>): Boolean {
        val recipe = recipeRepository.getRecipeOnce(recipeId) ?: return false
        val names = recipe.ingredients.map { it.name.trim() }.filter { it.isNotEmpty() }
        return names.isNotEmpty() && names.all { IngredientMatch.containsLike(pantry, it) }
    }

    /** Build a fresh week, keeping pinned days and leaving skipped days empty. Uses
     *  Multi-Restart so day 1 (which has no week-reuse anchor yet) doesn't lock in a
     *  greedy-suboptimal pick. */
    fun generateWeek() = viewModelScope.launch {
        _generating.value = true
        try {
        val candidates = recipeRepository.getAllRecipesOnce()
        if (candidates.isEmpty()) return@launch
        val today = LocalDate.now()
        val days = currentDays()
        val dateKeys = days.map(LocalDate::toString)
        val existing = mealPlanRepository.getForDates(dateKeys)
        val pinned = existing.filter { it.pinned }.associate { LocalDate.parse(it.date) to it.recipeId }
        val generated = MealPlanner.generateWeekBest(
            dates = days,
            skipped = emptySet(),
            pinned = pinned,
            candidates = candidates,
            recentlyPlanned = recentlyPlanned(days.first()),
            pantry = pantryRepository.stockedNames(),
            householdSize = settingsRepository.householdSizeOnce(),
            today = today,
            seed = System.currentTimeMillis(),
            liked = recipeRepository.likedRecipeIds(),
            lastCookedAt = cookedMap(candidates),
        )
        val ids = generated.mapKeys { it.key.toString() }.mapValues { it.value.recipeId }
        val reasons = generated.mapKeys { it.key.toString() }.mapValues { it.value.reasons }
        mealPlanRepository.generateAndSaveWeek(ids, dateKeys, reasons)
        } finally { _generating.value = false }
    }

    /** Re-roll a single day: pin the rest of the week, avoid the current dish, repick one day. */
    fun reroll(dateKey: String) = viewModelScope.launch {
        val candidates = recipeRepository.getAllRecipesOnce()
        if (candidates.isEmpty()) return@launch
        val today = LocalDate.now()
        val target = LocalDate.parse(dateKey)
        val days = currentDays()
        val dateKeys = days.map(LocalDate::toString)
        val existing = mealPlanRepository.getForDates(dateKeys)
        val currentByDate = existing.associate { LocalDate.parse(it.date) to it.recipeId }
        val others = currentByDate.filterKeys { it != target } // treat as fixed so only target changes
        // Penalise the current dish so the re-roll yields something different.
        val recently = recentlyPlanned(days.first()).toMutableMap()
        currentByDate[target]?.let { recently[it] = today }
        val generated = MealPlanner.generateWeek(
            dates = days,
            skipped = emptySet(),
            pinned = others,
            candidates = candidates,
            recentlyPlanned = recently,
            pantry = pantryRepository.stockedNames(),
            householdSize = settingsRepository.householdSizeOnce(),
            today = today,
            seed = System.nanoTime(),
            liked = recipeRepository.likedRecipeIds(),
            lastCookedAt = cookedMap(candidates),
        )
        generated[target]?.let { mealPlanRepository.replaceDay(dateKey, it.recipeId, it.reasons) }
    }

    /** recipeId -> the most recent date it was planned in the few weeks *before* [weekStart].
     *  Anchored on the generated week's first day (not on `today`) so the whole current week —
     *  including today's not-yet-cooked dish — counts as recent when planning next week, while
     *  regenerating the current week doesn't penalise it against itself. */
    private suspend fun recentlyPlanned(weekStart: LocalDate): Map<String, LocalDate> =
        mealPlanRepository.getForDateRange(weekStart.minusDays(HISTORY_DAYS).toString(), weekStart.minusDays(1).toString())
            .groupBy { it.recipeId }
            .mapValues { (_, entries) -> entries.maxOf { LocalDate.parse(it.date) } }

    /** recipeId -> last-cooked date, parsed from the recipe rows (feedback signal). */
    private fun cookedMap(candidates: List<com.food.opencook.data.local.relation.RecipeWithDetails>): Map<String, LocalDate> =
        candidates.mapNotNull { rwd ->
            rwd.recipe.lastCookedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { rwd.recipe.id to it }
        }.toMap()

    /** Add all planned recipes' ingredients to the shopping list, scaled to household size. */
    fun generateShoppingList(onDone: () -> Unit) = viewModelScope.launch {
        _generating.value = true
        try {
            val pantry = pantryRepository.stockedNames()
            val householdSize = settingsRepository.householdSizeOnce()
            // Always scoped to the currently-visible week: "what you see is what you shop for".
            val dateKeys = currentDateKeys()
            // One contribution per (recipe, day) so items carry their planned-day provenance.
            // Skip days already confirmed cooked — that meal happened, no need to shop for it.
            mealPlanRepository.getForDates(dateKeys)
                .filter { it.cookedAt == null }
                .distinctBy { it.recipeId to it.date }
                .forEach { entry ->
                    recipeRepository.getRecipeOnce(entry.recipeId)?.let { recipe ->
                        val scale = com.food.opencook.util.Numbers.scaleFor(recipe.recipe.servings, householdSize)
                        shoppingRepository.addFromRecipe(recipe, pantry, sourceDate = entry.date, scale = scale)
                    }
                }
            onDone()
        } finally { _generating.value = false }
    }

    private companion object {
        const val HISTORY_DAYS = 21L
        /** How far back an un-cooked, procured dish may still be rolled forward from. */
        const val LOOKBACK_DAYS = 3L
    }
}
