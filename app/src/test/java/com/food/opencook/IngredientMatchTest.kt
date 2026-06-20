package com.food.opencook

import com.food.opencook.util.IngredientMatch
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
    fun compoundNounHeadMatches() {
        // German compound nouns: head is on the right — having "Mehl" covers "Weizenmehl".
        assertTrue(IngredientMatch.matches("Mehl", "Weizenmehl"))
        assertTrue(IngredientMatch.matches("Weizenmehl", "Mehl"))
        assertTrue(IngredientMatch.matches("Salz", "Meersalz"))
        assertTrue(IngredientMatch.matches("Brot", "Vollkornbrot"))
        assertTrue(IngredientMatch.containsLike(setOf("mehl"), "Weizenmehl"))
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
    fun pantryGenericCoversSpecificIngredient() {
        // Generic pantry noun covers the adjective-qualified recipe form.
        assertTrue(IngredientMatch.covers("Pfeffer", "schwarzer Pfeffer"))
        assertTrue(IngredientMatch.covers("Muskatnuss", "geriebene Muskatnuss"))
        assertTrue(IngredientMatch.covers("Paprika", "rote Paprika"))
        assertTrue(IngredientMatch.covers("paprika", "Rote Paprika")) // case-insensitive
        // Covers also works through covers() → containsLike, the shopping-list pantry filter.
        assertTrue(IngredientMatch.containsLike(setOf("pfeffer"), "schwarzer Pfeffer"))
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
