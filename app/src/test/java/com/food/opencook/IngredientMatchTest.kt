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

import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.LearnedIngredientLinks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientMatchTest {

    @Test
    fun pluralAndSingularMatch() {
        assertTrue(IngredientMatch.matches("Zwiebel", "Zwiebeln"))
        assertTrue(IngredientMatch.matches("Zwiebeln", "Zwiebel"))
        assertTrue(IngredientMatch.matches("Tomate", "Tomaten"))
        assertTrue(IngredientMatch.matches("Champignon", "Champignons"))
        assertTrue(IngredientMatch.matches("salz", "Salz")) // case-insensitive
    }

    @Test
    fun distinctIngredientsDoNotMatch() {
        assertFalse(IngredientMatch.matches("Paprika", "Tomate"))
        assertFalse(IngredientMatch.matches("rote Paprika", "Paprika")) // no adjective merging
        assertFalse(IngredientMatch.matches("", "Zwiebel"))
    }

    @Test
    fun containsLikeChecksTheWholeSet() {
        assertTrue(IngredientMatch.containsLike(setOf("zwiebel", "lachs"), "Zwiebeln"))
        assertFalse(IngredientMatch.containsLike(setOf("lachs", "reis"), "Zwiebeln"))
    }

    @Test
    fun compoundNounHeadMatchesOnlyForStapleHeads() {
        // German compound nouns: head is on the right. We only generalize by the head when
        // that head is a *staple* — "Mehl"/"Öl"/"Salz" are, so their varieties collapse.
        assertTrue(IngredientMatch.matches("Mehl", "Weizenmehl"))
        assertTrue(IngredientMatch.matches("Weizenmehl", "Mehl"))
        assertTrue(IngredientMatch.matches("Salz", "Meersalz"))
        assertTrue(IngredientMatch.matches("Brühe", "Gemüsebrühe"))
        assertTrue(IngredientMatch.containsLike(setOf("mehl"), "Weizenmehl"))
        // A non-staple head does NOT generalize: distinct products that merely share a
        // suffix must stay apart (this is the fix for the reported bug).
        assertFalse(IngredientMatch.matches("Erbsen", "Kichererbsen"))
        assertFalse(IngredientMatch.matches("Milch", "Buttermilch"))
        assertFalse(IngredientMatch.matches("Brot", "Vollkornbrot"))
    }

    @Test
    fun compoundHeadGuardsAgainstFalsePositives() {
        // "Tomate" is NOT the right-most component of "Tomatenmark" → no match.
        assertFalse(IngredientMatch.matches("Tomate", "Tomatenmark"))
        // Symmetric matches() keeps adjective phrases distinct from their generic noun.
        // (Asymmetric coverage is handled by covers() — see below.)
        assertFalse(IngredientMatch.matches("Paprika", "rote Paprika"))
        assertFalse(IngredientMatch.matches("rote Paprika", "Paprika"))
        // Too-short stem must not absorb unrelated words.
        assertFalse(IngredientMatch.matches("Ei", "Eis"))
        assertFalse(IngredientMatch.matches("Reis", "Eis"))
    }

    @Test
    fun pantryStapleGenericCoversSpecificIngredient() {
        // A *staple* generic pantry noun covers the adjective-qualified recipe form —
        // having pepper means you don't buy a more specific pepper.
        assertTrue(IngredientMatch.covers("Pfeffer", "schwarzer Pfeffer"))
        assertTrue(IngredientMatch.covers("Muskatnuss", "geriebene Muskatnuss"))
        assertTrue(IngredientMatch.covers("Öl", "kaltgepresstes Öl"))
        // Covers also works through covers() → containsLike, the shopping-list pantry filter.
        assertTrue(IngredientMatch.containsLike(setOf("pfeffer"), "schwarzer Pfeffer"))
    }

    @Test
    fun pantryNonStapleGenericDoesNotCoverNamedVariety() {
        // The reported bug: generic "Bohnen" in the pantry must NOT drop "weiße Bohnen"
        // from the list — a named variety of a non-staple stays on the list to buy.
        assertFalse(IngredientMatch.covers("Bohnen", "weiße Bohnen"))
        assertFalse(IngredientMatch.covers("Paprika", "rote Paprika"))
        assertFalse(IngredientMatch.containsLike(setOf("bohnen"), "weiße Bohnen"))
    }

    @Test
    fun curatedDistinctPairsAreNeverMerged() {
        assertFalse(IngredientMatch.matches("Kichererbsen", "Erbsen"))
        assertFalse(IngredientMatch.matches("Buttermilch", "Milch"))
        assertFalse(IngredientMatch.covers("Zucker", "Puderzucker"))
        assertFalse(IngredientMatch.covers("Zucker", "brauner Zucker"))
    }

    @Test
    fun curatedSynonymsMatch() {
        assertTrue(IngredientMatch.matches("Frühlingszwiebel", "Lauchzwiebel"))
        assertTrue(IngredientMatch.matches("passierte Tomaten", "Passata"))
        assertTrue(IngredientMatch.containsLike(setOf("lauchzwiebel"), "Frühlingszwiebeln"))
    }

    @Test
    fun leakedLeadingAmountsAreStripped() {
        // The staple/coverage checks must fire even when a quantity leaked into the name.
        assertTrue(IngredientMatch.matches("3 Löffel Öl", "Öl"))
        assertTrue(IngredientMatch.matches("etwas Öl", "Öl"))
        assertTrue(IngredientMatch.matches("eine Prise Salz", "Salz"))
        assertTrue(IngredientMatch.matches("2 EL Olivenöl", "Olivenöl"))
    }

    @Test
    fun learnedDistinctionBlocksCoverage() {
        try {
            // Before the lesson, a staple head generalizes to its compound variety.
            assertTrue(IngredientMatch.covers("Mehl", "Vollkornmehl"))
            LearnedIngredientLinks.setDistinct(listOf("Vollkornmehl" to "Mehl"))
            // After the lesson, coverage is blocked in both directions.
            assertFalse(IngredientMatch.covers("Mehl", "Vollkornmehl"))
            assertFalse(IngredientMatch.matches("Vollkornmehl", "Mehl"))
        } finally {
            LearnedIngredientLinks.setDistinct(emptyList())
        }
    }

    @Test
    fun spellingVariantsAreFolded() {
        // "Soße" / "Sauce" are the same word in German cooking, with two spellings.
        assertTrue(IngredientMatch.matches("Sojasoße", "Sojasauce"))
        assertTrue(IngredientMatch.matches("Tomatensauce", "Tomatensoße"))
        // The pantry filter must catch the same equivalence.
        assertTrue(IngredientMatch.containsLike(setOf("Sojasauce"), "Sojasoße"))
        assertTrue(IngredientMatch.containsLike(setOf("Sojasoße"), "Sojasauce"))
        // Generic ß↔ss fallback for everything else (Swiss / post-1996 orthography).
        assertTrue(IngredientMatch.matches("Straße", "Strasse"))
    }

    @Test
    fun quantityParentheticalsAreStripped() {
        // The extractor sometimes glues a quantity onto the name. Two recipes calling the
        // same item with different parentheticals must still count as the same ingredient.
        assertTrue(IngredientMatch.matches("Hähnchenbrustfilets (ca. 400 g)", "Hähnchenbrustfilets"))
        assertTrue(IngredientMatch.matches("Kohlrabis (ca. 500g)", "Kohlrabi"))
        assertTrue(IngredientMatch.containsLike(setOf("rapsöl"), "Rapsöl (ca. 2 EL)"))
    }

    @Test
    fun trailingUsagePhrasesAreStripped() {
        // "zum Anbraten" / "nach Belieben" name a *use*, not a second ingredient — the head
        // before the preposition is the real item.
        assertTrue(IngredientMatch.matches("Butter zum Anbraten", "Butter"))
        assertTrue(IngredientMatch.matches("Salz nach Belieben", "Salz"))
        assertTrue(IngredientMatch.matches("Tomaten für die Soße", "Tomaten"))
        assertTrue(IngredientMatch.containsLike(setOf("butter"), "Butter zum Anbraten"))
        // Guard: a leading adjective is NOT a usage phrase, so it stays distinct as before.
        assertFalse(IngredientMatch.matches("rote Paprika", "Paprika"))
    }

    @Test
    fun pantrySpecificDoesNotCoverGenericIngredient() {
        // The specific pantry stock can't satisfy a generic recipe request — the recipe
        // might want a different variety, so the item stays on the shopping list.
        assertFalse(IngredientMatch.covers("schwarzer Pfeffer", "Pfeffer"))
        assertFalse(IngredientMatch.covers("rote Paprika", "Paprika"))
        // Unrelated tokens still don't match.
        assertFalse(IngredientMatch.covers("Salz", "schwarzer Pfeffer"))
        // Multi-word vs multi-word stays exact-only — no token gymnastics.
        assertFalse(IngredientMatch.covers("rote Paprika", "grüne Paprika"))
    }
}
