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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    ) = RecipeWithDetails(
        recipe = RecipeEntity(
            id = id, name = name, tags = tags, category = category,
            mealTypes = mealTypes, cookbook = cookbook, createdAt = 0, updatedAt = 0,
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
    ) = RecipeSearchFilter.matches(item, query, filters, likedIds, label)

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
