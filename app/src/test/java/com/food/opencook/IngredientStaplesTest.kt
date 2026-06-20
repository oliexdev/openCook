package com.food.opencook

import com.food.opencook.util.CommonGroceries
import com.food.opencook.util.IngredientMatch
import com.food.opencook.util.IngredientStaples
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientStaplesTest {

    @Test
    fun `every staple is also a known grocery (spelling guard)`() {
        // The match is plural-aware so "salz und pfeffer" — a combo form — is the only
        // entry that doesn't appear as a single word in CommonGroceries; everything else
        // must show up in the curated vocabulary to keep spellings consistent across the
        // app (the corrector, suggestion pool and add-fields all draw from there).
        val groceries = CommonGroceries.LIST
        val missing = IngredientStaples.ALL
            .filter { it != "salz und pfeffer" }
            .filter { !IngredientMatch.containsLike(groceries, it) }
        assertTrue(
            "Staples not found in CommonGroceries — fix the spelling: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `plural and singular forms are recognised`() {
        assertTrue(IngredientStaples.isStaple("Salz"))
        assertTrue(IngredientStaples.isStaple("salz"))
        assertTrue(IngredientStaples.isStaple("Brühen"))
        assertTrue(IngredientStaples.isStaple("Backpulver"))
    }

    @Test
    fun `culinary main ingredients are NOT staples`() {
        // These are deliberately excluded so the reuse / cardinality score stays meaningful.
        listOf("Zwiebel", "Knoblauch", "Ei", "Milch", "Petersilie",
               "Aubergine", "Champignons", "Hackfleisch", "Tomate").forEach {
            assertFalse("$it should NOT be a staple", IngredientStaples.isStaple(it))
        }
    }

    @Test
    fun `default pantry is a subset of staples (modulo case)`() {
        IngredientStaples.DEFAULT_PANTRY.forEach { item ->
            assertTrue(
                "$item is in DEFAULT_PANTRY but not recognised as staple",
                IngredientStaples.isStaple(item),
            )
        }
    }
}
