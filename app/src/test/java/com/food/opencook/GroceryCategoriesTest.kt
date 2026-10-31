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
import com.food.opencook.util.TextFold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals(GroceryCategory.OTHER, GroceryCategories.categorize("Batterien"))
    }

    @Test
    fun nonFoodAndSnackAislesWinOverFoodWords() {
        assertEquals(GroceryCategory.HOUSEHOLD, GroceryCategories.categorize("Klopapier"))
        assertEquals(GroceryCategory.HOUSEHOLD, GroceryCategories.categorize("Zahnpasta")) // not PANTRY "pasta"
        assertEquals(GroceryCategory.HOUSEHOLD, GroceryCategories.categorize("Spülmaschinensalz")) // not SPICES
        assertEquals(GroceryCategory.HOUSEHOLD, GroceryCategories.categorize("Deospray"))
        assertEquals(GroceryCategory.PETS, GroceryCategories.categorize("Katzenfutter"))
        assertEquals(GroceryCategory.SNACKS, GroceryCategories.categorize("Kartoffelchips")) // not PRODUCE
        assertEquals(GroceryCategory.SNACKS, GroceryCategories.categorize("Salzstangen"))
        // Food that merely looks similar stays where it was.
        assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("Snacktomaten"))
        assertTrue(GroceryCategories.categorize("Fideos") != GroceryCategory.HOUSEHOLD) // " deo" is anchored
    }

    @Test
    fun learnedNameCoversWholeWordVariants() {
        val overrides = mapOf("zahnpasta" to GroceryCategory.HOUSEHOLD, "salz" to GroceryCategory.OTHER)
        assertEquals(GroceryCategory.HOUSEHOLD, GroceryCategories.categorize("Zahnpasta sensitiv", overrides))
        assertEquals(GroceryCategory.HOUSEHOLD, GroceryCategories.categorize("Sensitiv-Zahnpasta, 75 ml", overrides))
        // Whole words only: "salz" must not reach into "Salzstangen".
        assertEquals(GroceryCategory.SNACKS, GroceryCategories.categorize("Salzstangen", overrides))
    }

    @Test
    fun learnedNameCarriesAcrossArabicSpellingVariants() {
        // Lesson without article/hamza; the item has both — same words after folding.
        val overrides = mapOf("معجون اسنان" to GroceryCategory.HOUSEHOLD)
        assertEquals(GroceryCategory.HOUSEHOLD, GroceryCategories.categorize("معجون الأسنان بالنعناع", overrides))
    }

    @Test
    fun exactThenLongestLessonWins() {
        val overrides = mapOf(
            "tomaten" to GroceryCategory.PRODUCE,
            "passierte tomaten" to GroceryCategory.PANTRY,
            "passierte tomaten bio" to GroceryCategory.OTHER,
        )
        assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("Tomaten gehackt", overrides))
        assertEquals(GroceryCategory.PANTRY, GroceryCategories.categorize("Passierte Tomaten 500 g", overrides))
        assertEquals(GroceryCategory.OTHER, GroceryCategories.categorize("passierte Tomaten bio", overrides))
    }

    @Test
    fun sectionsListEmptyAislesOnlyWhileDragging() {
        val grouped = mapOf(
            GroceryCategory.DAIRY to listOf("Milch"),
            GroceryCategory.PRODUCE to listOf("Lauch"),
        )
        assertEquals(
            listOf(GroceryCategory.PRODUCE, GroceryCategory.DAIRY),
            GroceryCategories.sections(grouped, includeEmpty = false).map { it.first },
        )
        val all = GroceryCategories.sections(grouped, includeEmpty = true)
        assertEquals(GroceryCategory.entries.toList(), all.map { it.first })
        assertEquals(emptyList<String>(), all.first { it.first == GroceryCategory.PETS }.second)
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

    /** Arabic keywords are folded like the names, so article and spelling variants still hit. */
    @Test
    fun arabicKeywordsIgnoreArticleAndKeepWholeWords() {
        val original = GroceryCategories.activeRules
        fun fold(vararg words: String) = words.map(TextFold::forMatch)
        try {
            GroceryCategories.setRules(
                listOf(
                    GroceryCategory.MEAT_FISH to fold("لحم مفروم", "دجاج"),
                    GroceryCategory.PRODUCE to fold("بصل", "ثوم"),
                    GroceryCategory.SPICES to fold("زيت ", "خل "),
                    GroceryCategory.PANTRY to fold("أرز"),
                ),
            )
            assertEquals(GroceryCategory.MEAT_FISH, GroceryCategories.categorize("الدجاج"))
            assertEquals(GroceryCategory.MEAT_FISH, GroceryCategories.categorize("لحم مفروم"))
            // "مفروم" alone means chopped: chopped onion stays with the vegetables.
            assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("بصل مفروم"))
            assertEquals(GroceryCategory.PANTRY, GroceryCategories.categorize("ارز"))
            // The trailing space keeps a keyword whole: oil and vinegar, not olives and pickles.
            assertEquals(GroceryCategory.SPICES, GroceryCategories.categorize("زيت"))
            assertEquals(GroceryCategory.SPICES, GroceryCategories.categorize("خل التفاح"))
            assertEquals(GroceryCategory.OTHER, GroceryCategories.categorize("زيتون"))
            assertEquals(GroceryCategory.OTHER, GroceryCategories.categorize("مخلل"))
        } finally {
            GroceryCategories.setRules(original)
        }
    }
}
