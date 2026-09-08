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

import com.food.opencook.util.IngredientLexicon
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.IngredientStaples
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ingredient matching used to be German-only grammar in code: head noun on the right, German
 * plural suffixes, a hard-coded list of German measure words. All of that is `arrays.xml` data
 * now, and this test drives the union the way `LocalizedLists` builds it at runtime — French
 * vocabulary next to the German/English one, because a household can hold recipes in both.
 */
class FrenchMatchingTest {

    private val savedVocabulary = IngredientMatch.activeVocabulary
    private val savedStaples = IngredientStaples.ALL
    private val savedPantry = IngredientStaples.DEFAULT_PANTRY
    private val savedSynonyms = IngredientLexicon.activeSynonyms
    private val savedDistinctions = IngredientLexicon.activeDistinctions

    @Before
    fun installUnionVocabulary() {
        IngredientMatch.setVocabulary(
            IngredientMatch.Vocabulary(
                leadingNoise = savedVocabulary.leadingNoise +
                    listOf("une pincée de", "un peu de", "c. à soupe", "gousse", "quelques"),
                usePhrases = savedVocabulary.usePhrases + "pour",
                pluralSuffixes = savedVocabulary.pluralSuffixes + "x",
                headConnectors = listOf("de", "d'", "du", "des", "à", "au", "aux"),
            ),
        )
        IngredientStaples.setData(
            all = savedStaples + setOf("huile", "huile d'olive", "sel", "sucre", "lait"),
            pantry = savedPantry,
        )
        IngredientLexicon.setData(
            synonyms = savedSynonyms + listOf(setOf("crème fraîche", "creme fraiche")),
            distinct = savedDistinctions +
                listOf("lait de coco" to "lait", "huile de sésame" to "huile"),
        )
    }

    @After
    fun restore() {
        IngredientMatch.setVocabulary(savedVocabulary)
        IngredientStaples.setData(savedStaples, savedPantry)
        IngredientLexicon.setData(savedSynonyms, savedDistinctions)
    }

    /** The point of the connector list: in French the head noun comes first. */
    @Test
    fun stapleCoversHeadInitialVariety() {
        assertTrue(IngredientMatch.covers("huile", "huile d'olive"))
        assertTrue(IngredientMatch.covers("huile", "huile de tournesol"))
        assertTrue(IngredientMatch.covers("sucre", "sucre de betterave"))
        // The typographic apostrophe is the same product.
        assertTrue(IngredientMatch.covers("huile", "huile d’olive"))
    }

    /** …and the reason it is a *list* and not "try both ends": English must not regress. */
    @Test
    fun headInitialRuleNeedsAConnector() {
        // "sugar" is a staple and stands first, but "snap" is no connector — so the head is
        // still "peas" and the sugar snap peas stay on the shopping list.
        assertFalse(IngredientMatch.covers("sugar", "sugar snap peas"))
        assertFalse(IngredientMatch.covers("sel", "sel snap peas"))
        // German keeps the head on the right.
        assertTrue(IngredientMatch.covers("Pfeffer", "schwarzer Pfeffer"))
    }

    /** A named variety that is not the staple must still be blocked. */
    @Test
    fun curatedDistinctionsSurviveHeadInitialMatching() {
        assertFalse(IngredientMatch.covers("lait", "lait de coco"))
        assertFalse(IngredientMatch.covers("huile", "huile de sésame"))
    }

    @Test
    fun frenchLeadingNoiseAndUsePhraseAreStripped() {
        assertEquals("sel", IngredientMatch.normalizeName("une pincée de sel"))
        assertEquals("sucre", IngredientMatch.normalizeName("2 c. à soupe sucre"))
        assertEquals("huile", IngredientMatch.normalizeName("huile pour la friture"))
        assertTrue(IngredientStaples.isStaple("une pincée de sel"))
    }

    @Test
    fun frenchPluralsMatchTheirSingular() {
        assertTrue(IngredientMatch.matches("gâteau", "gâteaux"))
        assertTrue(IngredientMatch.matches("carottes", "carotte"))
        // German plurals still work — the suffix lists are unioned, not replaced.
        assertTrue(IngredientMatch.matches("Zwiebel", "Zwiebeln"))
    }

    @Test
    fun lexiconSynonymsComeFromData() {
        assertTrue(IngredientMatch.matches("crème fraîche", "creme fraiche"))
    }
}
