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

package com.food.opencook.util

/**
 * Conservative, offline correction of misread ingredient names against a known
 * vocabulary (the suggestion pool: user data + Open Food Facts + the bundled German
 * ingredient lexicon). Deliberately cautious — it acts only on near-certain typos so it
 * never corrupts legitimate rare or foreign terms ("Sambal Oelek", "Crème fraîche"):
 *
 *  - name already known (incl. plural/singular variant) → leave it.
 *  - exactly **1** edit from a known term (clear OCR slip, e.g. "Petersile" → "Petersilie")
 *    → **auto-correct** (flagged so the user sees + can revert on the review screen).
 *  - **2** edits from a known term, for words ≥ 6 chars (e.g. "Olivöl" → "Olivenöl")
 *    → **suggest** ("Meinten Sie …?"), do not change.
 *  - anything further apart, or shorter than 4 chars → leave alone.
 *
 * Real-word errors ("Schweineleber" for "Schweinelendchen") are ≥3 edits from any term, so
 * they are intentionally left untouched — only a human (or a vision re-read) can catch those.
 *
 * Build one [Corrector] from the pool and reuse it for every ingredient (it precomputes the
 * known-key/stem sets once).
 */
object IngredientCorrection {

    /** Outcome for one ingredient name. [suggestion] is non-null only when not auto-corrected. */
    data class Result(val name: String, val suggestion: String?, val autoCorrected: Boolean) {
        companion object {
            fun unchanged(name: String) = Result(name, suggestion = null, autoCorrected = false)
        }
    }

    private const val MIN_LEN = 4          // below this, edit-distance is too noisy to trust
    private const val SUGGEST_MIN_LEN = 6  // only suggest a 2-edit match on longer words
    private val PLURAL_SUFFIXES = listOf("en", "n", "e", "s")

    fun corrector(pool: List<String>): Corrector = Corrector(pool)

    class Corrector internal constructor(pool: List<String>) {
        private val terms: List<String> = pool.map { it.trim() }.filter { it.isNotEmpty() }
        private val keys: Set<String> = terms.mapTo(HashSet()) { it.lowercase() }

        /**
         * Known if the word is a lexicon term, or a plural whose singular is ("Champignons"
         * → "Champignon", "Tomaten" → "Tomate"). Tries each suffix against the raw terms
         * (German plurals are irregular), so a correct plural is never "fixed" to singular.
         */
        private fun isKnown(key: String): Boolean {
            if (key in keys) return true
            return PLURAL_SUFFIXES.any { suf ->
                key.length - suf.length >= 3 && key.endsWith(suf) && key.dropLast(suf.length) in keys
            }
        }

        fun correct(rawName: String): Result {
            val name = rawName.trim()
            val key = name.lowercase()
            if (name.length < MIN_LEN) return Result.unchanged(rawName)
            if (isKnown(key)) return Result.unchanged(rawName)

            var best: String? = null
            var bestDist = Int.MAX_VALUE
            var bestPenalty = Int.MAX_VALUE
            var bestCount = 0
            for (term in terms) {
                val candKey = term.lowercase()
                if (kotlin.math.abs(candKey.length - key.length) > 2) continue
                val d = damerauLevenshtein(key, candKey, maxOf = 2)
                if (d > 2) continue
                // Tie-break toward a candidate that merely adds letters to the input (a model
                // drop-out): "olivöl" ⊂ "olivenöl" wins over same-distance "olive"/"oliven".
                val penalty = if (isSubsequence(key, candKey)) 0 else 1
                when {
                    d < bestDist || (d == bestDist && penalty < bestPenalty) -> {
                        bestDist = d; bestPenalty = penalty; best = term; bestCount = 1
                    }
                    d == bestDist && penalty == bestPenalty -> bestCount++
                }
            }
            val candidate = best ?: return Result.unchanged(rawName)
            return when {
                // One edit from a single clear best candidate → auto-correct (keep its casing).
                bestDist == 1 && bestCount == 1 -> Result(candidate, suggestion = null, autoCorrected = true)
                // Two edits on a longer word → suggest only (tie-broken toward the superset term).
                bestDist == 2 && key.length >= SUGGEST_MIN_LEN ->
                    Result(rawName, suggestion = candidate, autoCorrected = false)
                else -> Result.unchanged(rawName)
            }
        }
    }

    /** Is [a] a subsequence of [b] (every char of a appears in b, in order)? */
    private fun isSubsequence(a: String, b: String): Boolean {
        if (a.length > b.length) return false
        var i = 0
        for (c in b) if (i < a.length && a[i] == c) i++
        return i == a.length
    }

    /**
     * Damerau–Levenshtein distance (incl. adjacent transpositions) with an early exit once
     * the running minimum of a row exceeds [maxOf] — returns [maxOf] + 1 in that case, which
     * callers treat as "too far".
     */
    internal fun damerauLevenshtein(a: String, b: String, maxOf: Int): Int {
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > maxOf) return maxOf + 1
        val prev2 = IntArray(m + 1)
        val prev1 = IntArray(m + 1) { it }
        val curr = IntArray(m + 1)
        for (i in 1..n) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(
                    prev1[j] + 1,        // deletion
                    curr[j - 1] + 1,     // insertion
                    prev1[j - 1] + cost, // substitution
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prev2[j - 2] + 1) // transposition
                }
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxOf) return maxOf + 1
            System.arraycopy(prev1, 0, prev2, 0, m + 1)
            System.arraycopy(curr, 0, prev1, 0, m + 1)
        }
        return prev1[m]
    }
}
