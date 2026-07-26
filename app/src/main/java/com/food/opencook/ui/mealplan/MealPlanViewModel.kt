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
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.ShoppingRepository
import com.food.opencook.util.DateLabels
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.Numbers
import com.food.opencook.util.RecipeAvailability
import com.food.opencook.util.MealTypes
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
    /** Which meal of the day this dish sits in — already resolved, never null. */
    val slot: String,
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
/**
 * One meal row of a day. [dishes] is a list rather than a single dish because nothing in the
 * schema forbids two entries landing in the same cell (two devices planning offline, then
 * syncing) — showing both is honest and lets the user delete one, whereas silently hiding
 * the second would look like data loss.
 */
data class SlotPlan(
    val slot: String,
    val dishes: List<PlannedRecipe>,
    /** False when no recipe in the library is marked for this meal — the empty row then says
     *  so instead of opening a picker with nothing in it. */
    val hasCandidates: Boolean,
    /** True for a meal the household does not plan, filled as a one-off for this day. Those
     *  rows sit below the day instead of inside it — see [MealPlanSlots.rowsFor]. */
    val isExtra: Boolean = false,
)

data class DayPlan(val date: String, val label: String, val slots: List<SlotPlan>) {
    val entries: List<PlannedRecipe> get() = slots.flatMap { it.dishes }
}

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

    /** Which meals the household plans — drives how many rows a day card has. */
    val plannedMeals: StateFlow<List<String>> = settingsRepository.plannedMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanSlots.DEFAULT_PLANNED)

    val week: StateFlow<List<DayPlan>> = _selectedWeek
        .flatMapLatest { selection ->
            val days = daysFor(selection)
            val dateKeys = days.map(LocalDate::toString)
            combine(
                mealPlanRepository.observeForDates(dateKeys),
                recipeRepository.observeRecipes(),
                pantryRepository.observeItems(),
                settingsRepository.serverUrl,
                settingsRepository.plannedMeals,
            ) { entries, recipes, pantry, baseUrl, planned ->
                val byId = recipes.associateBy { it.recipe.id }
                val pantryNames = pantry.map { it.name.lowercase().trim() }.toSet()
                // Which meals the library can actually fill, so an empty row can say
                // "no breakfast recipes yet" instead of opening an empty picker.
                val withCandidates = MealTypes.KEYS.filter { slot ->
                    recipes.any { slot in MealTypes.fromStored(it.recipe.mealTypes) }
                }.toSet()
                val today = LocalDate.now().toString()
                days.map { day ->
                    val key = day.toString()
                    val bySlot = entries.filter { it.date == key }
                        .groupBy { MealPlanSlots.resolve(it.slot, planned) }
                    // A day that has gone by only shows what was actually planned — offering
                    // to fill yesterday's gaps is noise, and it keeps the current week short.
                    val rows = MealPlanSlots.rowsFor(planned, bySlot.keys)
                        .filter { key >= today || it in bySlot }
                    DayPlan(
                        date = key,
                        label = day.format(labelFormat),
                        slots = rows.map { slot ->
                            val dishes = bySlot[slot].orEmpty().map { entry ->
                                    val recipe = byId[entry.recipeId]
                                    val missingItems = recipe?.let { RecipeAvailability.missing(it, pantryNames) }
                                        .orEmpty()
                                    PlannedRecipe(
                                        entryId = entry.id,
                                        recipeId = entry.recipeId,
                                        slot = slot,
                                        name = recipe?.recipe?.name ?: "Rezept",
                                        pinned = entry.pinned,
                                        imageModel = com.food.opencook.ui.recipes.imageModelFor(recipe?.images.orEmpty(), baseUrl),
                                        missing = missingItems.size,
                                        missingItems = missingItems,
                                        // Reasons travel on the entity via reasonsJson — sync, restart-safe.
                                        reasons = mealPlanRepository.decodeReasons(entry.reasonsJson),
                                        cooked = entry.cookedAt != null,
                                    )
                            }
                            val hasCandidates = slot in withCandidates
                            SlotPlan(
                                slot = slot,
                                dishes = dishes,
                                hasCandidates = hasCandidates,
                                // Below the day, not in it: meals the household doesn't plan,
                                // and planned meals the library can't fill yet ("no breakfast
                                // recipes"). The latter is a note about the library, not part
                                // of the plan — it has no business sitting at the top of every
                                // single day. Once it holds a dish it counts as a real meal.
                                isExtra = slot !in planned || (dishes.isEmpty() && !hasCandidates),
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

    fun addRecipe(date: String, slot: String, recipeId: String) = viewModelScope.launch {
        mealPlanRepository.addEntry(date, recipeId, slot)
    }

    fun remove(entryId: String) = viewModelScope.launch { mealPlanRepository.deleteEntry(entryId) }

    /**
     * Put one planned dish's ingredients on the shopping list, straight from the row that
     * just said something is missing — otherwise it's a detour through the recipe screen.
     * [ShoppingRepository.addFromRecipe] is idempotent per (recipe, day) and the list hides
     * pantry-covered and staple items, so a second tap is harmless.
     */
    fun addToShoppingList(planned: PlannedRecipe, date: String, onDone: () -> Unit) = viewModelScope.launch {
        val recipe = recipeRepository.getRecipeOnce(planned.recipeId) ?: return@launch
        val scale = Numbers.scaleFor(recipe.recipe.servings, settingsRepository.householdSizeOnce())
        shoppingRepository.addFromRecipe(recipe, sourceDate = date, scale = scale)
        onDone()
    }

    /**
     * Drag-and-drop a planned dish onto another cell (day + meal). On an empty target the
     * dish just moves; on an occupied one the two cells swap. Shopping-list provenance
     * follows each dish via [moveSource], so already-listed ingredients keep pointing at
     * the day they're cooked (provenance is per day, not per meal — see the repository).
     */
    fun moveDish(entryId: String, fromDate: String, fromSlot: String, toDate: String, toSlot: String) =
        viewModelScope.launch {
            if (fromDate == toDate && fromSlot == toSlot) return@launch
            val planned = settingsRepository.plannedMealsOnce()
            val entries = mealPlanRepository.getForDates(listOf(fromDate, toDate))
            val dragged = entries.firstOrNull { it.id == entryId } ?: return@launch
            // Swap partner: an existing dish in the target cell (excluding the dragged one).
            val target = entries.firstOrNull {
                it.date == toDate && it.id != entryId && MealPlanSlots.resolve(it.slot, planned) == toSlot
            }

            mealPlanRepository.moveEntry(dragged.id, toDate, toSlot)
            shoppingRepository.moveSource(dragged.recipeId, fromDate, toDate)
            if (target != null) {
                mealPlanRepository.moveEntry(target.id, fromDate, fromSlot)
                shoppingRepository.moveSource(target.recipeId, toDate, fromDate)
            }
        }

    fun togglePin(entry: PlannedRecipe) = viewModelScope.launch {
        mealPlanRepository.setPinned(entry.entryId, !entry.pinned)
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

        val planned = settingsRepository.plannedMealsOnce()
        val windowKeys = (0..MealPlanReconciler.DEFAULT_WINDOW_DAYS).map { today.plusDays(it).toString() }
        val ahead = mealPlanRepository.getForDates(windowKeys)
        val pantry = pantryRepository.stockedNames()

        // Roll forward within the dish's own meal: a missed dinner belongs on another
        // evening, not on top of tomorrow's breakfast. So each meal is reconciled against
        // only the days already occupied *in that meal*.
        past.groupBy { MealPlanSlots.resolve(it.slot, planned) }.forEach { (slot, entries) ->
            val occupied = ahead.filter { MealPlanSlots.resolve(it.slot, planned) == slot }
                .map { LocalDate.parse(it.date) }.toSet()
            val candidates = entries.map { entry ->
                val procured = shoppingRepository.isProcured(entry.recipeId, entry.date) ||
                    fullyInPantry(entry.recipeId, pantry)
                MealPlanReconciler.PastEntry(entry.id, LocalDate.parse(entry.date), procured)
            }
            MealPlanReconciler.reconcile(candidates, occupied, emptySet(), today)
                .forEach { mealPlanRepository.moveEntry(it.entryId, it.toDate.toString(), slot) }
        }
    }

    /** All of a recipe's ingredients are covered by the pantry — same notion as the
     *  "Alles da" badge, so "procured" matches what the user already sees. */
    private suspend fun fullyInPantry(recipeId: String, pantry: Set<String>): Boolean {
        val recipe = recipeRepository.getRecipeOnce(recipeId) ?: return false
        val names = recipe.ingredients.map { it.name.trim() }.filter { it.isNotEmpty() }
        return names.isNotEmpty() && names.all { IngredientMatch.containsLike(pantry, it) }
    }

    /** Recipes the library offers for one meal. Empty means the user hasn't marked anything
     *  as e.g. breakfast yet — the caller then leaves that row alone instead of filling it
     *  with dinners. */
    private fun candidatesFor(slot: String, all: List<RecipeWithDetails>): List<RecipeWithDetails> =
        all.filter { slot in MealTypes.fromStored(it.recipe.mealTypes) }

    /**
     * Build a fresh week. Runs the planner once per planned meal, each over its own
     * candidate set and its own [MealPlanner.Weights.forSlot] tuning; pinned cells survive.
     *
     * The recency map accumulates across meals, so a dish used for lunch is heavily
     * penalised when dinner is planned (a future date yields 0 days' distance, i.e. the
     * maximum penalty) — and an exact same-day collision is dropped outright. Only the
     * *planned* meals are regenerated: a hand-placed one-off in a switched-off meal, like a
     * Sunday cake, is never touched.
     */
    fun generateWeek() = viewModelScope.launch {
        _generating.value = true
        try {
            val all = recipeRepository.getAllRecipesOnce()
            if (all.isEmpty()) return@launch
            val today = LocalDate.now()
            val days = currentDays()
            val dateKeys = days.map(LocalDate::toString)
            val planned = settingsRepository.plannedMealsOnce()
            val existing = mealPlanRepository.getForDates(dateKeys)
            val pantry = pantryRepository.stockedNames()
            val householdSize = settingsRepository.householdSizeOnce()
            val liked = recipeRepository.likedRecipeIds()
            val cooked = cookedMap(all)
            val recently = recentlyPlanned(days.first()).toMutableMap()
            // date -> recipes already placed that day by an earlier meal.
            val placed = mutableMapOf<String, MutableSet<String>>()
            existing.forEach { placed.getOrPut(it.date) { mutableSetOf() } += it.recipeId }

            planned.forEach { slot ->
                val candidates = candidatesFor(slot, all)
                if (candidates.isEmpty()) return@forEach
                val pinned = existing
                    .filter { it.pinned && MealPlanSlots.resolve(it.slot, planned) == slot }
                    .associate { LocalDate.parse(it.date) to it.recipeId }
                val generated = MealPlanner.generateWeekBest(
                    dates = days,
                    skipped = emptySet(),
                    pinned = pinned,
                    candidates = candidates,
                    recentlyPlanned = recently,
                    pantry = pantry,
                    householdSize = householdSize,
                    today = today,
                    seed = System.currentTimeMillis(),
                    liked = liked,
                    lastCookedAt = cooked,
                    weights = MealPlanner.Weights.forSlot(slot),
                ).filterNot { (date, pick) -> pick.recipeId in placed[date.toString()].orEmpty() }

                val ids = generated.mapKeys { it.key.toString() }.mapValues { it.value.recipeId }
                val reasons = generated.mapKeys { it.key.toString() }.mapValues { it.value.reasons }
                mealPlanRepository.generateAndSaveWeek(slot, ids, dateKeys, planned, reasons)
                generated.forEach { (date, pick) ->
                    recently[pick.recipeId] = date
                    placed.getOrPut(date.toString()) { mutableSetOf() } += pick.recipeId
                }
            }
        } finally { _generating.value = false }
    }

    /** Re-roll a single cell: pin the rest of that meal's week, avoid the current dish,
     *  repick just this one. */
    fun reroll(dateKey: String, slot: String) = viewModelScope.launch { pickFor(dateKey, slot) }

    /**
     * Fill every still-empty planned meal of one day. Sequential rather than parallel: each
     * pick reads the day back, so breakfast and dinner can't land on the same dish. This is
     * what the day-level wand does — with four meals planned, one tap beats four.
     */
    fun fillDay(dateKey: String) = viewModelScope.launch {
        val planned = settingsRepository.plannedMealsOnce()
        val taken = mealPlanRepository.getForDates(listOf(dateKey))
            .map { MealPlanSlots.resolve(it.slot, planned) }.toSet()
        planned.filterNot { it in taken }.forEach { pickFor(dateKey, it) }
    }

    private suspend fun pickFor(dateKey: String, slot: String) {
        val all = recipeRepository.getAllRecipesOnce()
        val candidates = candidatesFor(slot, all)
        if (candidates.isEmpty()) return
        val today = LocalDate.now()
        val target = LocalDate.parse(dateKey)
        val days = currentDays()
        val planned = settingsRepository.plannedMealsOnce()
        val existing = mealPlanRepository.getForDates(days.map(LocalDate::toString))
        val inSlot = existing.filter { MealPlanSlots.resolve(it.slot, planned) == slot }
        val currentByDate = inSlot.associate { LocalDate.parse(it.date) to it.recipeId }
        val others = currentByDate.filterKeys { it != target } // treat as fixed so only target changes
        // Penalise the current dish, and anything else already on that day in another meal,
        // so the re-roll yields something genuinely different.
        val recently = recentlyPlanned(days.first()).toMutableMap()
        currentByDate[target]?.let { recently[it] = today }
        val sameDay = existing.filter { it.date == dateKey }.map { it.recipeId }.toSet()
        val generated = MealPlanner.generateWeek(
            dates = days,
            skipped = emptySet(),
            pinned = others,
            candidates = candidates.filterNot { it.recipe.id in sameDay },
            recentlyPlanned = recently,
            pantry = pantryRepository.stockedNames(),
            householdSize = settingsRepository.householdSizeOnce(),
            today = today,
            seed = System.nanoTime(),
            liked = recipeRepository.likedRecipeIds(),
            lastCookedAt = cookedMap(all),
            weights = MealPlanner.Weights.forSlot(slot),
        )
        generated[target]?.let { mealPlanRepository.replaceCell(dateKey, slot, it.recipeId, planned, it.reasons) }
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
            val householdSize = settingsRepository.householdSizeOnce()
            // Always scoped to the currently-visible week: "what you see is what you shop for".
            val dateKeys = currentDateKeys()
            // One contribution per (recipe, day) so items carry their planned-day provenance.
            // Skip days already confirmed cooked — that meal happened, no need to shop for it.
            // Everything else planned is shopped for, whichever meal it sits in: if you took
            // the trouble to plan a breakfast, you want its ingredients in the house.
            // Pantry-covered / staple items are filtered at the view layer, not here.
            mealPlanRepository.getForDates(dateKeys)
                .filter { it.cookedAt == null }
                .distinctBy { it.recipeId to it.date }
                .forEach { entry ->
                    recipeRepository.getRecipeOnce(entry.recipeId)?.let { recipe ->
                        val scale = Numbers.scaleFor(recipe.recipe.servings, householdSize)
                        shoppingRepository.addFromRecipe(recipe, sourceDate = entry.date, scale = scale)
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
