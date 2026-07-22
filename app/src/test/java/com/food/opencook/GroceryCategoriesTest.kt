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

import com.food.opencook.util.GroceryCategories
import com.food.opencook.util.GroceryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class GroceryCategoriesTest {

    @Test
    fun categorizesCommonItems() {
        assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("Tomaten"))
        assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("2 rote Paprikaschoten"))
        assertEquals(GroceryCategory.MEAT_FISH, GroceryCategories.categorize("Barschfilet"))
        assertEquals(GroceryCategory.DAIRY, GroceryCategories.categorize("Vollmilch"))
        assertEquals(GroceryCategory.BAKERY, GroceryCategories.categorize("Brötchen"))
        assertEquals(GroceryCategory.SPICES, GroceryCategories.categorize("Olivenöl"))
    }

    @Test
    fun specificBeatsGeneric() {
        // "Kokosmilch" must NOT fall into DAIRY via "milch".
        assertEquals(GroceryCategory.PANTRY, GroceryCategories.categorize("Kokosmilch"))
    }

    @Test
    fun unknownFallsBackToOther() {
        assertEquals(GroceryCategory.OTHER, GroceryCategories.categorize("Klopapier"))
    }

    @Test
    fun learnedOverrideBeatsTheKeywordHeuristic() {
        // The household taught "Kokosmilch → DRINKS" — the user knows their store
        // better than our rules, so the lesson wins over the PANTRY keyword match.
        val overrides = mapOf("kokosmilch" to GroceryCategory.DRINKS)
        assertEquals(GroceryCategory.DRINKS, GroceryCategories.categorize("Kokosmilch", overrides))
        // Lookup normalizes case/whitespace the same way lessons are stored.
        assertEquals(GroceryCategory.DRINKS, GroceryCategories.categorize(" KOKOSMILCH ", overrides))
        // Names without a lesson keep the heuristic; empty map = old behavior.
        assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("Tomaten", overrides))
        assertEquals(GroceryCategory.PANTRY, GroceryCategories.categorize("Kokosmilch", emptyMap()))
    }

    @Test
    fun unionRecognisesBothLanguagesAndKeepsPriority() {
        // LocalizedLists unions every bundled content language into each category, so an
        // English-locale device with German recipes still groups them (and vice versa). The
        // category *order* is unchanged, so the "specific beats generic" priority survives.
        val original = GroceryCategories.activeRules
        try {
            GroceryCategories.setRules(
                listOf(
                    GroceryCategory.PANTRY to listOf("kokosmilch", "coconut milk"),
                    GroceryCategory.MEAT_FISH to listOf("hähnchen", "chicken"),
                    GroceryCategory.PRODUCE to listOf("zwiebel", "onion"),
                    GroceryCategory.DAIRY to listOf("milch", "milk"),
                ),
            )
            assertEquals(GroceryCategory.MEAT_FISH, GroceryCategories.categorize("Hähnchenbrust"))
            assertEquals(GroceryCategory.MEAT_FISH, GroceryCategories.categorize("chicken breast"))
            assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("Zwiebel"))
            assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("onion"))
            // PANTRY is listed before DAIRY → "Kokosmilch"/"coconut milk" stay out of DAIRY.
            assertEquals(GroceryCategory.PANTRY, GroceryCategories.categorize("Kokosmilch"))
            assertEquals(GroceryCategory.PANTRY, GroceryCategories.categorize("coconut milk"))
        } finally {
            GroceryCategories.setRules(original)
        }
    }
}
