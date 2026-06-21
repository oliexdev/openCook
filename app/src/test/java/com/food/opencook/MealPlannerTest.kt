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

package com.food.opencook

import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.ui.mealplan.MealPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun recipe(
    id: String,
    category: String,
    servings: Int? = null,
    ingredients: List<String> = emptyList(),
    totalTime: String? = null,
    prepTime: String? = null,
    cookTime: String? = null,
): RecipeWithDetails {
    val r = RecipeEntity(
        id = id, name = id, category = category, servings = servings, totalTime = totalTime,
        prepTime = prepTime, cookTime = cookTime,
        createdAt = 0, updatedAt = 0,
    )
    val ings = ingredients.mapIndexed { i, n -> IngredientEntity("$id-$i", id, i, null, null, n) }
    return RecipeWithDetails(r, ings, emptyList(), emptyList(), null)
}

private val NO_JITTER = MealPlanner.Weights(jitter = 0.0)

class MealPlannerTest {

    private val today = LocalDate.of(2026, 5, 25) // a Monday
    private fun week(n: Int) = (0 until n).map { today.plusDays(it.toLong()) }

    @Test
    fun avoidsRecentlyPlannedRecipe() {
        val a = recipe("A", "Pasta")
        val b = recipe("B", "Fleisch")
        val result = MealPlanner.generateWeek(
            dates = week(1), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(a, b),
            recentlyPlanned = mapOf("A" to today.minusDays(1)),
            pantry = emptySet(), householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals("B", result[today]?.recipeId) // A was just cooked → penalised
    }

    @Test
    fun neighbouringDaysGetDifferentCategories() {
        val result = MealPlanner.generateWeek(
            dates = week(2), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(recipe("A", "Pasta"), recipe("B", "Pasta"), recipe("C", "Fleisch")),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        val d0 = result[today]!!.recipeId
        val d1 = result[today.plusDays(1)]!!.recipeId
        val cat = mapOf("A" to "Pasta", "B" to "Pasta", "C" to "Fleisch")
        assertNotEquals(cat[d0], cat[d1])
    }

    @Test
    fun bigBatchSpansTwoConsecutiveDays() {
        val big = recipe("Big", "Suppe", servings = 4) // 4 >= 2 * household(2)
        val other = recipe("Other", "Salat", servings = 2)
        val result = MealPlanner.generateWeek(
            dates = week(3), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(big, other),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals("Big", result[today]?.recipeId)
        assertEquals("Big", result[today.plusDays(1)]?.recipeId) // leftovers
        assertEquals("Other", result[today.plusDays(2)]?.recipeId)
    }

    @Test
    fun respectsSkippedAndPinnedDays() {
        val result = MealPlanner.generateWeek(
            dates = week(3), skipped = setOf(today.plusDays(1)),
            pinned = mapOf(today to "P"),
            candidates = listOf(recipe("P", "Pasta"), recipe("Q", "Fleisch"), recipe("R", "Fisch")),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals("P", result[today]?.recipeId)                  // pinned kept
        assertFalse(result.containsKey(today.plusDays(1))) // skipped → no meal
        assertTrue(result.containsKey(today.plusDays(2)))  // free day filled
    }

    @Test
    fun isDeterministicForSameSeedAndValid() {
        val candidates = (1..6).map { recipe("R$it", if (it % 2 == 0) "Pasta" else "Fleisch") }
        val ids = candidates.map { it.recipe.id }.toSet()
        fun gen(seed: Long) = MealPlanner.generateWeek(
            dates = week(5), skipped = emptySet(), pinned = emptyMap(), candidates = candidates,
            recentlyPlanned = emptyMap(), pantry = emptySet(), householdSize = 2, today = today, seed = seed,
        )
        val a = gen(42)
        assertEquals(a, gen(42))                       // deterministic
        assertTrue(a.values.all { it.recipeId in ids })         // only valid recipes
    }

    @Test
    fun likedRecipeIsPreferred() {
        val a = recipe("A", "Pasta")
        val b = recipe("B", "Fleisch")
        val result = MealPlanner.generateWeek(
            dates = week(1), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(a, b),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1,
            liked = setOf("A"), weights = NO_JITTER,
        )
        assertEquals("A", result[today]?.recipeId) // liked → boosted over the neutral B
    }

    @Test
    fun recentlyCookedRecipeIsPenalised() {
        val a = recipe("A", "Pasta")
        val b = recipe("B", "Fleisch")
        val result = MealPlanner.generateWeek(
            dates = week(1), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(a, b),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1,
            lastCookedAt = mapOf("A" to today.minusDays(1)), weights = NO_JITTER,
        )
        assertEquals("B", result[today]?.recipeId) // A cooked yesterday → penalised
    }

    @Test
    fun cookableFromRanksByCoverage() {
        val a = recipe("A", "Sonstiges", ingredients = listOf("Ei", "Mehl"))
        val b = recipe("B", "Sonstiges", ingredients = listOf("Ei"))
        val c = recipe("C", "Sonstiges", ingredients = listOf("Fisch"))
        val ranked = MealPlanner.cookableFrom(setOf("ei", "mehl"), listOf(a, b, c)).map { it.recipe.id }
        assertEquals(listOf("A", "B"), ranked) // C needs fish (0 coverage) → excluded
    }

    @Test
    fun pantryCoverageMatchesPluralForms() {
        // Pantry holds the singular "Zwiebel"; the recipe lists the plural "Zwiebeln".
        // They must count as covered (plural-aware), so A beats the otherwise-tied B.
        val a = recipe("A", "Pasta", ingredients = listOf("Zwiebeln"))
        val b = recipe("B", "Pasta", ingredients = listOf("Lachs"))
        val result = MealPlanner.generateWeek(
            dates = week(1), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(b, a), // b first → would win a tie
            recentlyPlanned = emptyMap(), pantry = setOf("zwiebel"),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals("A", result[today]?.recipeId)
    }

    @Test
    fun timeFitFallsBackToPrepPlusCook() {
        // A has no totalTime but a quick prep+cook; B is slow. On a weekday A should win.
        val a = recipe("A", "Pasta", prepTime = "PT5M", cookTime = "PT10M")
        val b = recipe("B", "Pasta", totalTime = "PT120M")
        val result = MealPlanner.generateWeek(
            dates = week(1), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(b, a), // b first → would win a tie
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals("A", result[today]?.recipeId)
    }

    @Test
    fun uncategorisedRecipesAreNotMutuallyPenalised() {
        // Day 0 is a pinned "Sonstiges" dish. For day 1 a liked "Sonstiges" recipe must not
        // be penalised as "same category" against another uncategorised neighbour.
        val result = MealPlanner.generateWeek(
            dates = week(2), skipped = emptySet(), pinned = mapOf(today to "P"),
            candidates = listOf(recipe("P", "Sonstiges"), recipe("X", "Sonstiges"), recipe("Y", "Pasta")),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, liked = setOf("X"), weights = NO_JITTER,
        )
        assertEquals("X", result[today.plusDays(1)]?.recipeId) // liked wins; no bogus Sonstiges penalty
    }

    @Test
    fun leftoverNotPlacedWhenGapTooBig() {
        val big = recipe("Big", "Suppe", servings = 4)
        val result = MealPlanner.generateWeek(
            dates = week(4), skipped = emptySet(),
            pinned = mapOf(today.plusDays(1) to "X", today.plusDays(2) to "Y"),
            candidates = listOf(big, recipe("X", "Pasta"), recipe("Y", "Fisch"), recipe("Z", "Salat")),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals("Big", result[today]?.recipeId)
        // Next free day is 3 days later (days 1-2 pinned) → too far for leftovers.
        assertEquals(1, result.values.count { it.recipeId == "Big" })
    }

    @Test
    fun smallLibraryRotatesWithoutBackToBackRepeat() {
        val candidates = listOf(recipe("A", "Pasta"), recipe("B", "Fleisch"), recipe("C", "Fisch"))
        val result = MealPlanner.generateWeek(
            dates = week(7), skipped = emptySet(), pinned = emptyMap(), candidates = candidates,
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals(7, result.size)
        assertEquals(setOf("A", "B", "C"), result.values.map { it.recipeId }.toSet()) // all recipes used
        val days = week(7)
        assertTrue((0 until 6).none { result[days[it]]?.recipeId == result[days[it + 1]]?.recipeId })
    }

    @Test
    fun emptyCandidatesReturnsPinned() {
        val pinned = mapOf(today to "X")
        val result = MealPlanner.generateWeek(
            dates = week(2), skipped = emptySet(), pinned = pinned, candidates = emptyList(),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals(pinned, result.mapValues { it.value.recipeId })
    }

    @Test
    fun singleRecipeFillsEveryFreeDay() {
        val result = MealPlanner.generateWeek(
            dates = week(3), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(recipe("A", "Pasta")),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals(3, result.size)
        assertTrue(result.values.all { it.recipeId == "A" })
    }

    @Test
    fun staplesAreIgnoredInReuseAndCardinality() {
        // Both recipes share only "Salz" — a staple. A second recipe with a real
        // overlap ("Aubergine") must therefore win over the staples-only neighbour,
        // and the WEEK_REUSE reason on the winner must NOT mention salt.
        val anchor = recipe("anchor", "Pasta", ingredients = listOf("Aubergine", "Salz", "Olivenöl"))
        val realReuse = recipe("real", "Fleisch", ingredients = listOf("Aubergine", "Hackfleisch"))
        val staplesOnly = recipe("staples", "Fisch", ingredients = listOf("Salz", "Pfeffer", "Olivenöl", "Wasser"))
        val result = MealPlanner.generateWeek(
            dates = week(2), skipped = emptySet(),
            pinned = mapOf(today to "anchor"),
            candidates = listOf(anchor, staplesOnly, realReuse),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        assertEquals("real", result[today.plusDays(1)]?.recipeId)
        val reuse = result[today.plusDays(1)]?.reasons?.firstOrNull { it.code == MealPlanner.ReasonCode.WEEK_REUSE }
        assertEquals("aubergine", reuse?.detail) // staples filtered out of the detail
    }

    @Test
    fun reasonsContainNewIngredientsNeededWithNames() {
        // A solo recipe with no overlap has every non-staple ingredient as "to buy".
        val a = recipe("A", "Pasta", ingredients = listOf("Aubergine", "Champignons", "Salz"))
        val result = MealPlanner.generateWeek(
            dates = week(1), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(a),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        val needs = result[today]?.reasons?.firstOrNull { it.code == MealPlanner.ReasonCode.NEW_INGREDIENTS_NEEDED }
        assertTrue("needs reason missing", needs != null)
        // Detail lists the concrete non-staple items; "Salz" never appears.
        val detail = needs!!.detail.orEmpty()
        assertTrue("aubergine" in detail)
        assertTrue("champignons" in detail)
        assertTrue("salt should not be in needs detail", "salz" !in detail)
    }

    @Test
    fun pantryItemContributesToReuseReason() {
        val a = recipe("A", "Pasta", ingredients = listOf("Reis", "Sojasoße"))
        val result = MealPlanner.generateWeek(
            dates = week(1), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(a),
            recentlyPlanned = emptyMap(), pantry = setOf("reis"),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
        )
        val pantryReuse = result[today]?.reasons?.firstOrNull { it.code == MealPlanner.ReasonCode.PANTRY_REUSE }
        assertEquals("reis", pantryReuse?.detail)
        // Sojasoße is a staple — must not appear as something we need to buy.
        val needs = result[today]?.reasons?.firstOrNull { it.code == MealPlanner.ReasonCode.NEW_INGREDIENTS_NEEDED }
        assertTrue("sojasoße" !in (needs?.detail ?: ""))
    }

    @Test
    fun monotonyPenaltyCapsSameMainIngredientAfterOneReuse() {
        // Five chicken recipes plus enough non-chicken alternatives to fill the rest of
        // the week — mirroring a real library where the user has plenty of dishes to
        // choose from. With maxReuseCount=1 chicken should max out at 2 days (1 new +
        // 1 reuse); the planner has to pick from the other six recipes for the rest.
        val c1 = recipe("C1", "Fleisch", ingredients = listOf("Hähnchen", "Reis"))
        val c2 = recipe("C2", "Fleisch", ingredients = listOf("Hähnchen", "Curry"))
        val c3 = recipe("C3", "Fleisch", ingredients = listOf("Hähnchen", "Brokkoli"))
        val c4 = recipe("C4", "Fleisch", ingredients = listOf("Hähnchen", "Pasta"))
        val c5 = recipe("C5", "Fleisch", ingredients = listOf("Hähnchen", "Süßkartoffel"))
        val alts = listOf(
            recipe("V1", "Vegetarisch", ingredients = listOf("Tofu", "Reis")),
            recipe("V2", "Vegetarisch", ingredients = listOf("Linsen", "Spinat")),
            recipe("V3", "Vegetarisch", ingredients = listOf("Bohnen", "Quinoa")),
            recipe("F1", "Fisch", ingredients = listOf("Lachs", "Spargel")),
            recipe("F2", "Fisch", ingredients = listOf("Kabeljau", "Kartoffeln")),
            recipe("B1", "Rind", ingredients = listOf("Hackfleisch", "Pasta")),
        )
        val plan = MealPlanner.generateWeekBest(
            dates = week(5), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(c1, c2, c3, c4, c5) + alts,
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
            restarts = 8,
        )
        val chickenDays = plan.values.count { it.recipeId.startsWith("C") }
        assertTrue("got $chickenDays chicken days — cap should keep it ≤ 2", chickenDays <= 2)
    }

    @Test
    fun generateWeekBestPicksTheLeastShoppingHeavyPlan() {
        // Two recipes overlap heavily on a "real" ingredient; one stands alone. The
        // Multi-Restart wrapper must converge to a week that prefers the overlap.
        val r1 = recipe("R1", "Pasta", ingredients = listOf("Aubergine", "Reis"))
        val r2 = recipe("R2", "Pasta", ingredients = listOf("Aubergine", "Tofu"))
        val r3 = recipe("R3", "Pasta", ingredients = listOf("Lachs", "Spargel"))
        val plan = MealPlanner.generateWeekBest(
            dates = week(2), skipped = emptySet(), pinned = emptyMap(),
            candidates = listOf(r1, r2, r3),
            recentlyPlanned = emptyMap(), pantry = emptySet(),
            householdSize = 2, today = today, seed = 1, weights = NO_JITTER,
            restarts = 8,
        )
        val ids = plan.values.map { it.recipeId }.toSet()
        assertTrue("R1 and R2 reuse aubergine → both should be picked over R3", ids == setOf("R1", "R2"))
    }
}
