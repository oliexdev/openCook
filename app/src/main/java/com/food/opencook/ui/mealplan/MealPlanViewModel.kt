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
import com.food.opencook.data.local.entity.ShoppingItemEntity
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
import com.food.opencook.util.PlanWindow
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

    /** The day the rolling window is centred on. Held as state rather than read inline so the
     *  screen can re-anchor it (see [refreshToday]) — a phone left on the planner overnight
     *  would otherwise keep calling yesterday "today". */
    private val _today = MutableStateFlow(LocalDate.now())
    val todayKey: StateFlow<String> = _today
        .map(LocalDate::toString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _today.value.toString())

    /** Re-centre the window on the actual current date; called when the screen is resumed. */
    fun refreshToday() {
        val now = LocalDate.now()
        if (now != _today.value) _today.value = now
    }

    /** One-time swipe peek-hint gate for the planner, same convention as pantry/shopping.
     *  Defaults to `true` so a slow DataStore read never flashes the hint at someone who
     *  has already seen it. */
    val swipeHintSeen: StateFlow<Boolean> =
        settingsRepository.swipeHintSeenPlan
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun markSwipeHintSeen() = viewModelScope.launch { settingsRepository.setSwipeHintSeenPlan() }

    /** Which meals the household plans — drives how many rows a day card has. */
    val plannedMeals: StateFlow<List<String>> = settingsRepository.plannedMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealPlanSlots.DEFAULT_PLANNED)

    val week: StateFlow<List<DayPlan>> = _today
        .flatMapLatest { anchor ->
            val days = PlanWindow.days(anchor)
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
                val today = anchor.toString()
                days.map { day ->
                    val key = day.toString()
                    val bySlot = entries.filter { it.date == key }
                        .groupBy { MealPlanSlots.resolve(it.slot, planned) }
                    // A day that has gone by only shows what was actually planned — offering
                    // to fill yesterday's gaps is noise, and it keeps the retrospective short.
                    // Every meal the household plans keeps its row on a day still to come,
                    // in its place in the day, whether or not the library can fill it yet:
                    // the settings define the shape of a day, and a missing breakfast recipe
                    // is a reason to go and pick one, not a reason to hide the breakfast.
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
                            SlotPlan(
                                slot = slot,
                                dishes = dishes,
                                hasCandidates = slot in withCandidates,
                                // Below the day, not in it: meals the household doesn't plan,
                                // shown only because a one-off was placed there anyway.
                                isExtra = slot !in planned,
                            )
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The retrospective teaser above the oldest day. Null while the household has never
     * confirmed a meal — an entry into an empty screen is worse than no entry at all.
     *
     * [monthCooked] counts the current calendar month, which is what the card advertises; the
     * screen behind it reaches back a year.
     */
    data class RetrospectTeaser(val monthCooked: Int)

    val retrospect: StateFlow<RetrospectTeaser?> = _today
        .flatMapLatest { anchor ->
            combine(
                mealPlanRepository.observeCookedTotal(),
                mealPlanRepository.observeCookedSince(anchor.withDayOfMonth(1).toString()),
            ) { total, thisMonth ->
                if (total == 0) null
                else RetrospectTeaser(thisMonth.count { it.cookedAt?.let { d -> d <= anchor.toString() } == true })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True while a week is being generated / shopping list built — drives a loading UI. */
    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** Recipes to choose from when assigning to a day. */
    val recipeOptions: StateFlow<List<RecipeOption>> =
        recipeRepository.observeRecipes()
            .map { list -> list.map { RecipeOption(it.recipe.id, it.recipe.name ?: "—") } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Drop a planned dish, and with it the shopping lines only that dish put on the list — a
     * plan you deleted should not keep sending you shopping. What was already ticked off, or
     * shared with another dish, stays; see [ShoppingRepository.removeContributionOf].
     *
     * The lines only go once the dish is off the plan **entirely**: cooking the same thing on
     * Tuesday and Friday consolidates into one set of lines, so deleting the Tuesday must
     * leave them for the Friday. Days already past don't count towards "still planned" —
     * nobody shops for a meal that has been and gone.
     */
    fun remove(planned: PlannedRecipe, date: String) = viewModelScope.launch {
        mealPlanRepository.deleteEntry(planned.entryId)
        val stillComing = mealPlanRepository.isPlannedFrom(planned.recipeId, LocalDate.now().toString())
        lastRemovedItems =
            if (stillComing) emptyList() else shoppingRepository.removeContributionOf(planned.recipeId)
    }

    /** Shopping lines pulled by the last [remove], kept for that deletion's Undo. */
    private var lastRemovedItems: List<ShoppingItemEntity> = emptyList()

    /** Undo a [remove]: the dish goes back on the day (as a fresh entry) and its shopping
     *  lines are restored exactly as they were. */
    fun undoRemove(planned: PlannedRecipe, date: String) = viewModelScope.launch {
        mealPlanRepository.addEntry(date, planned.recipeId, planned.slot)
        shoppingRepository.importItems(lastRemovedItems)
        lastRemovedItems = emptyList()
    }

    /**
     * Put one planned dish's ingredients on the shopping list, straight from the row that
     * just said something is missing — otherwise it's a detour through the recipe screen.
     * [ShoppingRepository.addFromRecipe] is idempotent per (recipe, day) and the list hides
     * pantry-covered and staple items, so a second tap is harmless.
     */
    fun addToShoppingList(planned: PlannedRecipe, date: String, onDone: (canUndo: Boolean) -> Unit) =
        viewModelScope.launch {
            val recipe = recipeRepository.getRecipeOnce(planned.recipeId) ?: return@launch
            val scale = Numbers.scaleFor(recipe.recipe.servings, settingsRepository.householdSizeOnce())
            lastShoppingAdd = shoppingRepository.addFromRecipe(recipe, sourceDate = date, scale = scale)
            // No undo when nothing changed — the call is idempotent per (recipe, day), so a
            // second swipe adds nothing and there is correspondingly nothing to take back.
            onDone(lastShoppingAdd != null)
        }

    /** The most recent single-dish add, held only long enough for the snackbar's Undo. */
    private var lastShoppingAdd: ShoppingRepository.ShoppingAddUndo? = null

    fun undoAddToShoppingList() = viewModelScope.launch {
        lastShoppingAdd?.let {
            shoppingRepository.undoAddFromRecipe(it)
            lastShoppingAdd = null
        }
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
     * The rolling planner. Fills every day of the forward window the planner has not yet been
     * offered — which, opened daily, is the single day that just rolled in at the far end, and
     * after a long absence is the whole window at once.
     *
     * Three properties make this safe to run unattended:
     *  - it only ever fills an **empty** cell, and marks the day afterwards, so it never
     *    returns to a day the household has since edited (deleting included);
     *  - a dish planned or cooked within [AUTO_FILL_GAP_DAYS] is barred outright, and if that
     *    leaves nothing the day stays empty rather than looping through a small library;
     *  - an empty library marks nothing, so a household that adds recipes later still gets its
     *    window filled instead of having burned it while it had none.
     *
     * Runs once per planned meal, each over its own candidate set and its own
     * [MealPlanner.Weights.forSlot] tuning. The recency map accumulates across meals, so a
     * dish used for lunch is heavily penalised when dinner is planned, and an exact same-day
     * collision is dropped outright.
     */
    fun autoFillWindow() = viewModelScope.launch {
        // Deliberately without the `generating` progress bar: this runs on every open, usually
        // for a single day, and a spinner flashing each time would announce housekeeping the
        // user never asked for. The rows simply appear.
        run {
            val all = recipeRepository.getAllRecipesOnce()
            // Nothing to plan from: leave the days unmarked so they get a real chance later.
            if (all.isEmpty()) return@launch
            val today = LocalDate.now()
            val windowKeys = PlanWindow.futureDays(today).map(LocalDate::toString)
            val flagged = mealPlanRepository.autoPlannedDates(windowKeys)
            val days = PlanWindow.autoFillDates(today, flagged)
            if (days.isEmpty()) return@launch

            val dateKeys = days.map(LocalDate::toString)
            val planned = settingsRepository.plannedMealsOnce()
            val inWindow = mealPlanRepository.getForDates(windowKeys)
            val pantry = pantryRepository.stockedNames()
            val householdSize = settingsRepository.householdSizeOnce()
            val liked = recipeRepository.likedRecipeIds()
            val cooked = cookedMap(all)
            val recently = recentlyPlanned(today).toMutableMap()
            // Dishes already standing in the window count towards the cool-down as well.
            // `recentlyPlanned` only looks *backwards*, and filling just the far edge leaves
            // the days in between invisible to it — without this, tomorrow's dinner could be
            // planned again for the last day of the window, six days later.
            inWindow.forEach { entry ->
                val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@forEach
                val previous = recently[entry.recipeId]
                if (previous == null || date.isAfter(previous)) recently[entry.recipeId] = date
            }
            // date -> recipes already placed that day by an earlier meal.
            val placed = mutableMapOf<String, MutableSet<String>>()
            inWindow.filter { it.date in dateKeys }
                .forEach { placed.getOrPut(it.date) { mutableSetOf() } += it.recipeId }
            var anySlotPlannable = false

            planned.forEach { slot ->
                val candidates = candidatesFor(slot, all)
                if (candidates.isEmpty()) return@forEach
                anySlotPlannable = true
                val generated = MealPlanner.generateWeekBest(
                    dates = days,
                    skipped = emptySet(),
                    pinned = emptyMap(),
                    candidates = candidates,
                    recentlyPlanned = recently,
                    pantry = pantry,
                    householdSize = householdSize,
                    today = today,
                    seed = System.currentTimeMillis(),
                    liked = liked,
                    lastCookedAt = cooked,
                    weights = MealPlanner.Weights.forSlot(slot),
                    minRepeatGapDays = AUTO_FILL_GAP_DAYS,
                ).filterNot { (date, pick) -> pick.recipeId in placed[date.toString()].orEmpty() }

                generated.forEach { (date, pick) ->
                    mealPlanRepository.autoFillCell(
                        date.toString(), slot, pick.recipeId, planned, pick.reasons,
                    )
                    recently[pick.recipeId] = date
                    placed.getOrPut(date.toString()) { mutableSetOf() } += pick.recipeId
                }
            }

            // Mark whether or not a dish came out of it — but only if at least one meal had a
            // candidate list to work from. A day declined for lack of recipes must not be
            // retried every single day until something slips through.
            if (anySlotPlannable) dateKeys.forEach { mealPlanRepository.markAutoPlanned(it) }
        }
    }

    /** What the planner would put in one cell, and why. Null when it has nothing to offer. */
    data class Suggestion(
        val recipeId: String,
        val reasons: List<MealPlanner.ReasonContribution>,
    )

    private val _suggestion = MutableStateFlow<Suggestion?>(null)

    /** The planner's proposal for the cell the picker was opened for — shown at the top of the
     *  list so choosing and being surprised are the same screen, not two different buttons. */
    val suggestion: StateFlow<Suggestion?> = _suggestion.asStateFlow()

    fun loadSuggestion(dateKey: String, slot: String) = viewModelScope.launch {
        _suggestion.value = computeSuggestion(dateKey, slot)
    }

    /**
     * Put [recipeId] in the cell, replacing whatever sits there. Replacing rather than adding
     * is what makes one screen serve both the empty row and the swap button: an empty cell has
     * nothing to clear, so the two collapse into the same operation. Pinned dishes survive
     * (see [MealPlanRepository.replaceCell]).
     *
     * [reasons] travel only when the user took the planner's own proposal — a hand-picked dish
     * has no score breakdown to explain, and its "why" button stays hidden.
     */
    fun choose(
        dateKey: String,
        slot: String,
        recipeId: String,
        reasons: List<MealPlanner.ReasonContribution> = emptyList(),
        onDone: () -> Unit = {},
    ) = viewModelScope.launch {
        mealPlanRepository.replaceCell(dateKey, slot, recipeId, settingsRepository.plannedMealsOnce(), reasons)
        onDone()
    }

    /**
     * Run the planner for one cell: every other day of that meal is treated as fixed, the dish
     * currently in the cell is penalised so the proposal is genuinely different, and anything
     * already on that day in another meal is excluded outright.
     */
    private suspend fun computeSuggestion(dateKey: String, slot: String): Suggestion? {
        val all = recipeRepository.getAllRecipesOnce()
        val candidates = candidatesFor(slot, all)
        if (candidates.isEmpty()) return null
        val today = LocalDate.now()
        val target = runCatching { LocalDate.parse(dateKey) }.getOrNull() ?: return null
        // The planner only returns picks for dates it was given, and the caller reads the
        // target back out of that map — so the context window has to span every day the user
        // can tap, not just the seven the top-bar action writes to.
        val days = PlanWindow.futureDays(today)
        if (target !in days) return null
        val planned = settingsRepository.plannedMealsOnce()
        val existing = mealPlanRepository.getForDates(days.map(LocalDate::toString))
        val inSlot = existing.filter { MealPlanSlots.resolve(it.slot, planned) == slot }
        val currentByDate = inSlot.associate { LocalDate.parse(it.date) to it.recipeId }
        val others = currentByDate.filterKeys { it != target } // treat as fixed so only target changes
        // Penalise the current dish, and anything else already on that day in another meal,
        // so the re-roll yields something genuinely different.
        val recently = recentlyPlanned(today).toMutableMap()
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
        return generated[target]?.let { Suggestion(it.recipeId, it.reasons) }
    }

    /** recipeId -> the most recent date it was planned in the few weeks *before* [from].
     *  Anchored on the first day being generated, which is today: everything already cooked
     *  counts as recent, while the days about to be overwritten don't penalise themselves. */
    private suspend fun recentlyPlanned(from: LocalDate): Map<String, LocalDate> =
        mealPlanRepository.getForDateRange(from.minusDays(HISTORY_DAYS).toString(), from.minusDays(1).toString())
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
            // Scoped to the same seven days the "suggest" action plans: today and the six
            // that follow. Shopping for a day that has already gone by makes no sense.
            val dateKeys = PlanWindow.actionDays().map(LocalDate::toString)
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

        /**
         * How long a dish is barred from the rolling planner after it was last planned or
         * cooked. Ten days is long enough that a household with a decent library never sees a
         * pattern, and short enough that a fortnight's plan still fills up. Below that many
         * candidates the days simply stay empty — which is the honest answer.
         *
         * Applies to the unattended fill only: the single-cell suggestion in the picker
         * ignores it, because someone who deliberately asks for an alternative wants one.
         */
        const val AUTO_FILL_GAP_DAYS = 10L
        /** How far back an un-cooked, procured dish may still be rolled forward from. */
        const val LOOKBACK_DAYS = 3L
    }
}
