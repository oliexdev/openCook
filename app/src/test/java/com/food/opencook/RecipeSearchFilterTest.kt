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
import com.food.opencook.ui.recipes.RecipeFilters
import com.food.opencook.ui.recipes.RecipeSearchFilter
import com.food.opencook.util.CookedFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The recipe list's search/filter predicate: full text over name, tags, ingredients,
 * cookbook and category label; sheet filters ANDed between groups, OR within a group;
 * an unset mealTypes column counts as the lunch+dinner default.
 */
class RecipeSearchFilterTest {

    /** Test double for the localized category label (German, as in the app). */
    private val label: (String?) -> String = { key ->
        when (key) {
            "soup" -> "Suppe"
            "dessert" -> "Dessert"
            else -> "Sonstiges"
        }
    }

    private fun recipe(
        id: String = "r1",
        name: String = "Lauchsuppe",
        tags: String? = "schnell",
        category: String? = "soup",
        mealTypes: String? = null,
        cookbook: String? = "Omas Küche",
        ingredients: List<String> = listOf("Lauch", "Kartoffeln"),
        lastCookedAt: String? = null,
    ) = RecipeWithDetails(
        recipe = RecipeEntity(
            id = id, name = name, tags = tags, category = category,
            mealTypes = mealTypes, cookbook = cookbook, lastCookedAt = lastCookedAt,
            createdAt = 0, updatedAt = 0,
        ),
        ingredients = ingredients.mapIndexed { i, n -> IngredientEntity("i$i", id, i, null, null, n) },
        instructions = emptyList(),
        images = emptyList(),
        nutrition = null,
    )

    private fun matches(
        item: RecipeWithDetails,
        query: String = "",
        filters: RecipeFilters = RecipeFilters(),
        likedIds: Set<String> = emptySet(),
        pantry: Set<String> = emptySet(),
        today: LocalDate = LocalDate.of(2026, 8, 17),
    ) = RecipeSearchFilter.matches(item, query, filters, likedIds, pantry, today, label)

    // --- cooking status (single-select group) ---

    @Test
    fun cookedFilterSplitsTheRotation() {
        val today = LocalDate.of(2026, 8, 17)
        val never = recipe(lastCookedAt = null)
        val recent = recipe(lastCookedAt = today.minusDays(5).toString())
        val forgotten = recipe(lastCookedAt = today.minusDays(200).toString())

        val cooked = RecipeFilters(cooked = CookedFilter.COOKED)
        assertTrue(matches(recent, filters = cooked, today = today))
        assertTrue(matches(forgotten, filters = cooked, today = today))
        assertFalse(matches(never, filters = cooked, today = today))

        val nie = RecipeFilters(cooked = CookedFilter.NEVER)
        assertTrue(matches(never, filters = nie, today = today))
        assertFalse(matches(recent, filters = nie, today = today))

        // "Forgotten" deliberately includes never-cooked — those are the most forgotten of all.
        val stale = RecipeFilters(cooked = CookedFilter.STALE)
        assertTrue(matches(forgotten, filters = stale, today = today))
        assertTrue(matches(never, filters = stale, today = today))
        assertFalse(matches(recent, filters = stale, today = today))
    }

    @Test
    fun cookedFilterCountsAsOneActiveFilter() {
        assertEquals(0, RecipeFilters().activeCount)
        assertEquals(1, RecipeFilters(cooked = CookedFilter.STALE).activeCount)
        assertEquals(2, RecipeFilters(cooked = CookedFilter.STALE, likedOnly = true).activeCount)
    }

    @Test
    fun cookedFilterAndsWithTheOtherGroups() {
        val today = LocalDate.of(2026, 8, 17)
        val r = recipe(category = "soup", lastCookedAt = null)
        val filters = RecipeFilters(cooked = CookedFilter.NEVER, categories = setOf("dessert"))
        assertFalse(matches(r, filters = filters, today = today))
    }

    // --- cookable now ---

    @Test
    fun cookableOnlyKeepsDishesThePantryCoversEndToEnd() {
        val r = recipe(ingredients = listOf("Lauch", "Kartoffeln"))
        val filters = RecipeFilters(cookableOnly = true)
        assertTrue(matches(r, filters = filters, pantry = setOf("lauch", "kartoffeln")))
        assertFalse(matches(r, filters = filters, pantry = setOf("lauch")))
        // Off by default: the same recipe passes with an empty pantry.
        assertTrue(matches(r, pantry = emptySet()))
    }

    @Test
    fun cookableOnlyIgnoresStaplesAndToleratesPlurals() {
        // Salt is never in the pantry list, and "Zwiebel" is covered by "Zwiebeln".
        val r = recipe(ingredients = listOf("Zwiebel", "Salz"))
        assertTrue(
            matches(r, filters = RecipeFilters(cookableOnly = true), pantry = setOf("zwiebeln")),
        )
    }

    // --- full text ---

    @Test
    fun fullTextMatchesIngredientCategoryLabelAndCookbook() {
        val r = recipe()
        assertTrue(matches(r, query = "lauchsuppe"))    // name
        assertTrue(matches(r, query = "schnell"))       // tag
        assertTrue(matches(r, query = "kartoffel"))     // ingredient — "cook with what I have"
        assertTrue(matches(r, query = "Suppe"))         // localized category label
        assertTrue(matches(r, query = "omas"))          // cookbook
        assertFalse(matches(r, query = "Schokolade"))
    }

    @Test
    fun categoryLabelIsNotConsultedForUncategorizedRecipes() {
        // A null category must not match via its fallback label ("Sonstiges").
        val r = recipe(name = "Experiment", tags = null, category = null, ingredients = emptyList(), cookbook = null)
        assertFalse(matches(r, query = "Sonstiges"))
    }

    // --- sheet filters ---

    @Test
    fun unsetMealTypesCountAsLunchAndDinner() {
        val unset = recipe(mealTypes = null)
        assertTrue(matches(unset, filters = RecipeFilters(mealTypes = setOf("dinner"))))
        assertFalse(matches(unset, filters = RecipeFilters(mealTypes = setOf("breakfast"))))

        val cake = recipe(id = "r2", name = "Marmorkuchen", mealTypes = "snack")
        assertTrue(matches(cake, filters = RecipeFilters(mealTypes = setOf("snack"))))
        assertFalse(matches(cake, filters = RecipeFilters(mealTypes = setOf("dinner"))))
    }

    @Test
    fun multiSelectIsOrWithinAGroupAndBetweenGroups() {
        val soup = recipe()
        // OR within the category group …
        assertTrue(matches(soup, filters = RecipeFilters(categories = setOf("soup", "dessert"))))
        // … AND between groups: category matches but mealType does not.
        assertFalse(
            matches(soup, filters = RecipeFilters(categories = setOf("soup"), mealTypes = setOf("breakfast"))),
        )
    }

    @Test
    fun cookbooksAreMultiSelect() {
        val oma = recipe()                                    // cookbook "Omas Küche"
        val jamie = recipe(id = "r2", cookbook = "Jamie")
        val none = recipe(id = "r3", cookbook = null)
        val filter = RecipeFilters(cookbooks = setOf("Omas Küche", "Jamie"))
        assertTrue(matches(oma, filters = filter))
        assertTrue(matches(jamie, filters = filter))
        assertFalse(matches(none, filters = filter))
        // Empty selection = all cookbooks, including recipes without one.
        assertTrue(matches(none))
    }

    @Test
    fun likedOnlyFiltersByHouseholdLikes() {
        val r = recipe()
        assertFalse(matches(r, filters = RecipeFilters(likedOnly = true)))
        assertTrue(matches(r, filters = RecipeFilters(likedOnly = true), likedIds = setOf("r1")))
    }

    @Test
    fun queryAndFiltersCombine() {
        val r = recipe()
        assertTrue(matches(r, query = "lauch", filters = RecipeFilters(categories = setOf("soup"))))
        assertFalse(matches(r, query = "lauch", filters = RecipeFilters(categories = setOf("dessert"))))
    }
}
