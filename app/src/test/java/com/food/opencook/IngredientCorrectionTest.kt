package com.food.opencook

import com.food.opencook.util.CommonGroceries
import com.food.opencook.util.IngredientCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientCorrectionTest {

    private val pool = listOf(
        "Olivenöl", "Petersilie", "Zwiebel", "Knoblauchzehe", "Paprikaschote",
        "Sambal Oelek", "Crème fraîche", "Schweinelende", "Frühlingszwiebel",
        "Mehl", "Salz", "Hähnchenbrust",
    )
    private val corrector = IngredientCorrection.corrector(pool)

    @Test
    fun `exact and plural-or-singular variants are left unchanged`() {
        for (n in listOf("Olivenöl", "olivenöl", "Zwiebeln", "Petersilie", "Schweinelende")) {
            val r = corrector.correct(n)
            assertEquals(n, r.name)
            assertNull(r.suggestion)
            assertFalse(r.autoCorrected)
        }
    }

    @Test
    fun `single-edit typo is auto-corrected`() {
        val r = corrector.correct("Petersilei") // transposition of the last two letters
        assertTrue(r.autoCorrected)
        assertEquals("Petersilie", r.name)
        assertNull(r.suggestion)
    }

    @Test
    fun `two-edit near miss on a longer word is suggested, not changed`() {
        val r = corrector.correct("Olivöl") // distance 2 from "Olivenöl"
        assertFalse(r.autoCorrected)
        assertEquals("Olivöl", r.name)
        assertEquals("Olivenöl", r.suggestion)
    }

    @Test
    fun `legit rare or foreign term is never touched`() {
        val r = corrector.correct("Sambal Oelek")
        assertEquals("Sambal Oelek", r.name)
        assertNull(r.suggestion)
        assertFalse(r.autoCorrected)
    }

    @Test
    fun `real-word error too far from any term is not auto-corrected`() {
        // "Schweineleber" is a valid word but ≥3 edits from "Schweinelende" → must NOT snap.
        assertFalse(corrector.correct("Schweineleber").autoCorrected)
    }

    @Test
    fun `short tokens are ignored`() {
        val r = corrector.correct("Eis")
        assertEquals("Eis", r.name)
        assertNull(r.suggestion)
        assertFalse(r.autoCorrected)
    }

    @Test
    fun `damerau levenshtein basics including early exit`() {
        assertEquals(1, IngredientCorrection.damerauLevenshtein("abc", "abd", 5))
        assertEquals(1, IngredientCorrection.damerauLevenshtein("ab", "ba", 5)) // transposition
        assertEquals(2, IngredientCorrection.damerauLevenshtein("olivöl", "olivenöl", 5))
        assertEquals(6, IngredientCorrection.damerauLevenshtein("abcdef", "uvwxyz", 5)) // capped → maxOf+1
    }

    /** Safety gate against the full bundled vocabulary (CommonGroceries). */
    @Test
    fun `bundled vocabulary never corrupts valid distinct terms`() {
        val c = IngredientCorrection.corrector(CommonGroceries.LIST)
        assertFalse(c.correct("Schweineleber").autoCorrected)
        val sambal = c.correct("Sambal Oelek")
        assertFalse(sambal.autoCorrected)
        assertNull(sambal.suggestion)
        // A genuine near-miss should still be offered against the full list.
        assertEquals("Olivenöl", c.correct("Olivöl").suggestion)
    }

    /**
     * Effectiveness + safety gate on the *actual* misreads observed from the test photos,
     * run against the real bundled lexicon. Documents what the dictionary layer does and
     * doesn't fix (skipped if the file isn't on disk).
     */
    @Test
    fun `bundled vocabulary - behaviour on observed misreads`() {
        val c = IngredientCorrection.corrector(CommonGroceries.LIST)

        // Genuine fixes the dictionary CAN make:
        assertEquals("Olivenöl", c.correct("Olivöl").suggestion)          // 2 edits → suggest
        val olivendoel = c.correct("Olivendöl")                          // 1 edit → auto
        assertTrue(olivendoel.autoCorrected)
        assertEquals("Olivenöl", olivendoel.name)

        // Plural/singular variant must not be needlessly flipped.
        val zehen = c.correct("Knoblauchzehen")
        assertFalse(zehen.autoCorrected)
        assertNull(zehen.suggestion)

        // Safety: aggressive or real-word misreads must never be silently changed.
        assertFalse(c.correct("Olivengelb").autoCorrected)        // too far (4 edits)
        assertFalse(c.correct("Artschokkendosen").autoCorrected)  // too far
        assertFalse(c.correct("Schweineleber").autoCorrected)     // valid word, wrong meaning
    }
}
