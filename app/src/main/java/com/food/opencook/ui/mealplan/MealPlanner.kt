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

import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.util.DurationFormat
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.IngredientStaples
import com.food.opencook.util.RecipeCategories
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Pure meal-plan scoring — no Android/Room/IO, so it's directly unit-testable.
 *
 * [generateWeek] fills the free (non-skipped, non-pinned) days greedily, each day
 * picking the highest-scoring recipe. The score balances **variety** (penalise
 * recently-planned recipes and the same category as a neighbouring day) against
 * **shoppability** (reward reusing ingredients already in the week, penalise each
 * new ingredient the recipe forces onto the shopping list), with small nudges
 * for time-fit (quicker on weekdays) and pantry coverage, plus seeded jitter so
 * "re-roll" yields a different — but still good — plan. A big-batch recipe
 * (servings ≥ 2× household) is also placed on the next free day as leftovers.
 *
 * Staples (salt, pepper, oil, broth, …) are filtered out before reuse/cardinality
 * are measured — see [IngredientStaples] — so "salt in Mon + Wed" stops dominating
 * the reuse signal.
 *
 * The result carries a [PickedRecipe] per day with the per-factor score
 * breakdown so the UI can explain *why* a dish was chosen instead of just
 * surfacing a recipe id.
 */
object MealPlanner {

    data class Weights(
        val recencyPenalty: Double = 6.0,   // for a same-day repeat; decays over the window
        val recencyWindowDays: Long = 21,
        val sameCategoryPenalty: Double = 3.0,
        val newItemPenalty: Double = 1.2,    // per non-staple ingredient new to the week
        val weekReuseBonus: Double = 1.5,    // per non-staple ingredient the week already plans
        val monotonyPenalty: Double = 2.0,   // per repeat beyond [maxReuseCount] of the same ingredient
        val maxReuseCount: Int = 1,          // an ingredient may appear in this many *additional* days as a bonus
                                             // (so total = 1 first occurrence + maxReuseCount reuses = 2 days max by default)
        val pantryReuseBonus: Double = 1.5,  // per non-staple ingredient available in pantry
        val timeFitBonus: Double = 1.5,
        val jitter: Double = 0.6,            // tie-breaker only; below the smallest soft signal
        val likedBonus: Double = 2.0,            // recipe liked by ≥1 member
        val cookedRecentlyPenalty: Double = 4.0, // cooked within the window; decays
        val cookedWindowDays: Long = 10,
    )

    /** Leftovers are only carried to a free day at most this many days later. */
    private const val LEFTOVER_MAX_GAP_DAYS = 2L

    /** A code for each kind of score contribution so the UI can render it in plain
     *  German via a single mapper. Negative codes (penalties) and positive codes
     *  (boni) live in the same list — the UI separates them by sign. */
    enum class ReasonCode {
        WEEK_REUSE,              // overlaps with ingredients of another planned day
        PANTRY_REUSE,            // overlaps with the pantry
        LIKED,                   // a household member liked it
        QUICK_WEEKDAY,           // short total time, scheduled on a weekday
        BIG_BATCH_LEFTOVER,      // recipe serves ≥2× household → carried as leftover

        NEW_INGREDIENTS_NEEDED,  // adds new items to the shopping list
        RECENTLY_PLANNED,        // already planned within the recency window
        RECENTLY_COOKED,         // actually cooked within the cooked window
        SAME_CATEGORY_NEIGHBOUR, // same real category as the day before/after
        MONOTONY,                // a main ingredient (e.g. chicken) is already used 2+ times this week
    }

    /** One factor's contribution to a recipe's score. [detail] carries optional
     *  context (e.g. the concrete ingredient names that overlap with the week).
     *  `@Serializable` so the repository can persist the reasons across app restarts
     *  without re-running the planner (which couldn't reproduce the same state anyway). */
    @Serializable
    data class ReasonContribution(
        val code: ReasonCode,
        val weight: Double,
        val detail: String? = null,
    )

    /** A picked recipe with its score and the per-factor breakdown that produced it. */
    data class PickedRecipe(
        val recipeId: String,
        val score: Double,
        val reasons: List<ReasonContribution>,
    )

    fun generateWeek(
        dates: List<LocalDate>,
        skipped: Set<LocalDate>,
        pinned: Map<LocalDate, String>,
        candidates: List<RecipeWithDetails>,
        recentlyPlanned: Map<String, LocalDate>,
        pantry: Set<String>,
        householdSize: Int,
        today: LocalDate,
        seed: Long,
        liked: Set<String> = emptySet(),
        lastCookedAt: Map<String, LocalDate> = emptyMap(),
        weights: Weights = Weights(),
        coreByRecipe: Map<String, Set<String>>? = null,
    ): Map<LocalDate, PickedRecipe> {
        if (candidates.isEmpty()) {
            return pinned.mapValues { (_, id) ->
                PickedRecipe(id, score = 0.0, reasons = emptyList())
            }
        }

        val byId = candidates.associateBy { it.recipe.id }
        // Pre-filter staples once per recipe — without this every score() re-filtered the
        // same ~15 names through the ~30 staples list, multiplied by candidates × days ×
        // restarts. On larger libraries that's millions of String compares.
        val core: Map<String, Set<String>> = coreByRecipe ?: candidates.associate { rwd ->
            rwd.recipe.id to rwd.ingredientNames().filterNot { IngredientStaples.isStaple(it) }.toSet()
        }
        val result = LinkedHashMap<LocalDate, PickedRecipe>()
        pinned.forEach { (date, id) ->
            if (id in byId) result[date] = PickedRecipe(id, score = 0.0, reasons = emptyList())
        }

        val used = result.values.map { it.recipeId }.toMutableSet()
        val maxMinutes = 90.0

        for (date in dates) {
            if (date in skipped || result.containsKey(date)) continue

            // Fair rotation: when every recipe has been used, start a fresh cycle but keep
            // yesterday's dish out, so a small library never repeats back-to-back.
            var pool = candidates.filter { it.recipe.id !in used }
            if (pool.isEmpty()) {
                val yesterday = result[date.minusDays(1)]?.recipeId
                used.clear()
                if (yesterday != null) used += yesterday
                pool = candidates.filter { it.recipe.id !in used }.ifEmpty { candidates }
            }

            val rng = Random(seed xor date.toEpochDay() * 1_000_003L)
            val placedIds = result.mapValues { it.value.recipeId }
            // Per-ingredient *counts* across the week (not just presence) so we can
            // saturate the reuse bonus and penalise monotony when a main ingredient
            // (chicken, salmon …) starts dominating the whole week.
            val weekCoreCounts: Map<String, Int> = placedIds.values
                .mapNotNull { core[it] }
                .flatten()
                .groupingBy { it }
                .eachCount()
            val best = pool
                .map { it to score(it, date, placedIds, byId, recentlyPlanned, pantry, core, weekCoreCounts, maxMinutes, liked, lastCookedAt, weights, rng) }
                .maxByOrNull { it.second.first }
                ?: continue
            val (recipe, scored) = best
            val (s, reasons) = scored

            result[date] = PickedRecipe(recipe.recipe.id, s, reasons)
            used += recipe.recipe.id

            // Big batch → eat it again as leftovers, but only on a nearby free day
            // (a leftover days later would be stale → skip it instead).
            val servings = recipe.recipe.servings
            if (servings != null && servings >= householdSize * 2) {
                dates.firstOrNull { it.isAfter(date) && it !in skipped && it !in pinned && !result.containsKey(it) }
                    ?.takeIf { ChronoUnit.DAYS.between(date, it) <= LEFTOVER_MAX_GAP_DAYS }
                    ?.let { leftoverDay ->
                        val leftoverReason = ReasonContribution(ReasonCode.BIG_BATCH_LEFTOVER, weight = 0.0)
                        result[leftoverDay] = PickedRecipe(recipe.recipe.id, score = s, reasons = listOf(leftoverReason))
                    }
            }
        }
        return result
    }

    /**
     * Runs [generateWeek] [restarts] times with derived seeds and returns the plan with
     * the best **weekly aggregate**: sum of per-day scores minus a Cardinality
     * surcharge for the total number of distinct non-staple ingredients the week
     * needs to buy. Greedy alone can be locally optimal on day 1 (no week-reuse to
     * lean on yet) — Multi-Restart compensates by trying several starting points.
     *
     * Re-rolls (single day) bypass this; they want a predictable jitter from the
     * user-chosen seed, not a sweep.
     */
    fun generateWeekBest(
        dates: List<LocalDate>,
        skipped: Set<LocalDate>,
        pinned: Map<LocalDate, String>,
        candidates: List<RecipeWithDetails>,
        recentlyPlanned: Map<String, LocalDate>,
        pantry: Set<String>,
        householdSize: Int,
        today: LocalDate,
        seed: Long,
        liked: Set<String> = emptySet(),
        lastCookedAt: Map<String, LocalDate> = emptyMap(),
        weights: Weights = Weights(),
        restarts: Int = 4,
    ): Map<LocalDate, PickedRecipe> {
        // Compute staple-filtered ingredient sets once for the whole sweep — the same
        // recipes are scored [restarts] × [days] times, no point re-filtering each pass.
        val coreByRecipe: Map<String, Set<String>> = candidates.associate { rwd ->
            rwd.recipe.id to rwd.ingredientNames().filterNot { IngredientStaples.isStaple(it) }.toSet()
        }
        var bestPlan: Map<LocalDate, PickedRecipe>? = null
        var bestAggregate = Double.NEGATIVE_INFINITY
        repeat(restarts) { i ->
            val plan = generateWeek(
                dates, skipped, pinned, candidates, recentlyPlanned, pantry,
                householdSize, today, seed = seed + i, liked = liked,
                lastCookedAt = lastCookedAt, weights = weights,
                coreByRecipe = coreByRecipe,
            )
            val scoreSum = plan.values.sumOf { it.score }
            // Aggregate Cardinality: count distinct non-staple ingredients across all
            // planned dishes that aren't in the pantry. Higher = more shopping → worse.
            val needs = plan.values.flatMap { picked -> coreByRecipe[picked.recipeId].orEmpty() }
                .toSet()
                .filterNot { IngredientMatch.containsLike(pantry, it) }
            val aggregate = scoreSum - needs.size * weights.newItemPenalty * 0.5
            if (aggregate > bestAggregate) {
                bestAggregate = aggregate
                bestPlan = plan
            }
        }
        return bestPlan ?: emptyMap()
    }

    private fun score(
        recipe: RecipeWithDetails,
        date: LocalDate,
        placed: Map<LocalDate, String>,
        byId: Map<String, RecipeWithDetails>,
        recentlyPlanned: Map<String, LocalDate>,
        pantry: Set<String>,
        coreByRecipe: Map<String, Set<String>>,
        weekCoreCounts: Map<String, Int>,
        maxMinutes: Double,
        liked: Set<String>,
        lastCookedAt: Map<String, LocalDate>,
        w: Weights,
        rng: Random,
    ): Pair<Double, List<ReasonContribution>> {
        var s = 0.0
        val reasons = mutableListOf<ReasonContribution>()
        // Only a real (non-default) category drives the variety penalty — two uncategorised
        // dishes ("Sonstiges") are unrelated and must not penalise each other.
        val category = recipe.recipe.category
            ?.let { RecipeCategories.normalizeKey(it) }
            ?.takeIf { it != RecipeCategories.DEFAULT }
        val coreIngredients = coreByRecipe[recipe.recipe.id].orEmpty()

        // Variety: recency penalty (closer = larger).
        recentlyPlanned[recipe.recipe.id]?.let { last ->
            val days = ChronoUnit.DAYS.between(last, date).coerceAtLeast(0)
            if (days < w.recencyWindowDays) {
                val d = -w.recencyPenalty * (1.0 - days.toDouble() / w.recencyWindowDays)
                s += d
                reasons += ReasonContribution(ReasonCode.RECENTLY_PLANNED, d, detail = days.toString())
            }
        }
        // Feedback: penalise recently *cooked* dishes (decays over the window).
        lastCookedAt[recipe.recipe.id]?.let { cooked ->
            val days = ChronoUnit.DAYS.between(cooked, date).coerceAtLeast(0)
            if (days < w.cookedWindowDays) {
                val d = -w.cookedRecentlyPenalty * (1.0 - days.toDouble() / w.cookedWindowDays)
                s += d
                reasons += ReasonContribution(ReasonCode.RECENTLY_COOKED, d, detail = days.toString())
            }
        }
        // Feedback: boost dishes a household member liked.
        if (recipe.recipe.id in liked) {
            s += w.likedBonus
            reasons += ReasonContribution(ReasonCode.LIKED, w.likedBonus)
        }
        // Variety: same (real) category as a neighbouring day.
        if (category != null) {
            listOf(date.minusDays(1), date.plusDays(1)).forEach { neighbour ->
                val neighbourCategory = placed[neighbour]?.let { byId[it]?.recipe?.category }
                    ?.let { RecipeCategories.normalizeKey(it) }
                if (neighbourCategory == category) {
                    s -= w.sameCategoryPenalty
                    reasons += ReasonContribution(ReasonCode.SAME_CATEGORY_NEIGHBOUR, -w.sameCategoryPenalty, detail = category)
                }
            }
        }
        // Shoppability split into two halves on the same non-staple ingredient set:
        //  + Reuse-Bonus per ingredient the week already plans (so the user opens one
        //    bunch of parsley and uses it twice).
        //  − Cardinality-Penalty per ingredient the week doesn't yet plan and the pantry
        //    doesn't have either (each extra unique item is one more thing to buy).
        if (coreIngredients.isNotEmpty()) {
            // Bucket each non-staple ingredient based on (a) how often the week already
            // contains it (plural-aware) and (b) whether the pantry has it. One bucket
            // per ingredient — no double counting between reuse/pantry/cardinality.
            val reusedDetail = mutableListOf<String>()
            val monotonyDetail = mutableListOf<String>()
            val pantryDetail = mutableListOf<String>()
            val needsDetail = mutableListOf<String>()
            var reuseScore = 0.0
            var monotonyScore = 0.0
            var pantryScore = 0.0
            var needsScore = 0.0
            for (ing in coreIngredients) {
                val countSoFar = countLike(weekCoreCounts, ing)
                val inPantry = IngredientMatch.containsLike(pantry, ing)
                when {
                    countSoFar in 1..w.maxReuseCount -> {
                        // Bonus only for the first [maxReuseCount] week-appearances. After that
                        // an ingredient stops paying — that's how we stop the planner from
                        // glueing 5 chicken dishes together because every chicken-shaped recipe
                        // looked locally optimal.
                        reuseScore += w.weekReuseBonus
                        reusedDetail += ing
                    }
                    countSoFar > w.maxReuseCount -> {
                        // 3+ already → adding another is monotonous; penalise it stronger
                        // the more it's been used. With monotonyPenalty=2.0 and bonus=1.5
                        // a 4th chicken-day nets ~-3 vs. the lure of cardinality.
                        val pen = -w.monotonyPenalty * (countSoFar - w.maxReuseCount).toDouble()
                        monotonyScore += pen
                        monotonyDetail += ing
                    }
                    inPantry -> {
                        pantryScore += w.pantryReuseBonus
                        pantryDetail += ing
                    }
                    else -> {
                        needsScore -= w.newItemPenalty
                        needsDetail += ing
                    }
                }
            }
            if (reuseScore != 0.0) {
                s += reuseScore
                reasons += ReasonContribution(ReasonCode.WEEK_REUSE, reuseScore, detail = reusedDetail.joinToString(", "))
            }
            if (monotonyScore != 0.0) {
                s += monotonyScore
                reasons += ReasonContribution(ReasonCode.MONOTONY, monotonyScore, detail = monotonyDetail.joinToString(", "))
            }
            if (pantryScore != 0.0) {
                s += pantryScore
                reasons += ReasonContribution(ReasonCode.PANTRY_REUSE, pantryScore, detail = pantryDetail.joinToString(", "))
            }
            if (needsScore != 0.0) {
                s += needsScore
                reasons += ReasonContribution(ReasonCode.NEW_INGREDIENTS_NEEDED, needsScore, detail = needsDetail.joinToString(", "))
            }
        }
        // Time-fit: prefer quicker meals on weekdays.
        if (date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            effectiveMinutes(recipe)?.let { mins ->
                val bonus = w.timeFitBonus * (1.0 - (mins / maxMinutes).coerceIn(0.0, 1.0))
                if (bonus > 0) {
                    s += bonus
                    reasons += ReasonContribution(ReasonCode.QUICK_WEEKDAY, bonus, detail = mins.toInt().toString())
                }
            }
        }
        s += rng.nextDouble() * w.jitter
        return s to reasons
    }

    /**
     * Recipes cookable from [available] ingredient names, best coverage first.
     * Powers the shopping "ingredient not found → suggest an alternative" flow.
     */
    fun cookableFrom(available: Set<String>, candidates: List<RecipeWithDetails>): List<RecipeWithDetails> =
        candidates
            .map { it to coverage(it, available) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }

    private fun coverage(recipe: RecipeWithDetails, available: Set<String>): Double {
        val names = recipe.ingredientNames()
        if (names.isEmpty()) return 0.0
        return names.count { IngredientMatch.containsLike(available, it) }.toDouble() / names.size
    }

    /** Plural-aware count: how often does any key in [counts] match [name]? Sums all
     *  hits so plural and singular forms ("Zwiebel" + "Zwiebeln") contribute together. */
    private fun countLike(counts: Map<String, Int>, name: String): Int =
        counts.entries.filter { IngredientMatch.matches(it.key, name) }.sumOf { it.value }

    /** Total time in minutes, falling back to prep + cook when totalTime is absent. */
    private fun effectiveMinutes(recipe: RecipeWithDetails): Double? {
        DurationFormat.minutes(recipe.recipe.totalTime)?.let { return it.toDouble() }
        val prep = DurationFormat.minutes(recipe.recipe.prepTime)
        val cook = DurationFormat.minutes(recipe.recipe.cookTime)
        return if (prep != null || cook != null) ((prep ?: 0) + (cook ?: 0)).toDouble() else null
    }
}

private fun RecipeWithDetails.ingredientNames(): Set<String> =
    ingredients.map { it.name.lowercase().trim() }.filter { it.isNotEmpty() }.toSet()
