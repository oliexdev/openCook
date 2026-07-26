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
 * Plural/singular-aware ingredient-name matching for pantry coverage, ingredient reuse
 * (shoppability), the "missing items" badge and shopping-list pantry skipping.
 *
 * Purely a comparison **predicate** — it never rewrites stored names. It layers, in
 * priority order:
 *   1. **Distinctions** ([LearnedIngredientLinks] taught by the user, then curated
 *      [IngredientLexicon] pairs) → short-circuit to *not the same*, blocking coverage.
 *   2. **Synonyms** ([IngredientLexicon]) → same product ("Frühlingszwiebel" ↔ "Lauchzwiebel").
 *   3. normalize-equality and German plural suffixes.
 *   4. **Compound-noun head** ("Weizenmehl" ↔ "Mehl") — but only when the head is itself a
 *      staple ([IngredientStaples.isStapleWord]). This gate is what stops "Kichererbsen"
 *      collapsing into "Erbsen" or "Buttermilch" into "Milch": those heads aren't staples.
 *
 * For pantry-vs-recipe coverage use [covers] (asymmetric): a *staple* generic pantry noun
 * covers a more specific recipe ingredient ("Pfeffer" covers "schwarzer Pfeffer"), but a
 * non-staple generic ("Bohnen") does not cover a named variety ("weiße Bohnen") — leave it
 * on the list. Use [matches] (symmetric) for "are these the same item?".
 */
object IngredientMatch {

    private val PLURAL_SUFFIXES = listOf("en", "n", "e", "s")
    private const val MIN_STEM = 3

    /** Quantity / prep note glued onto a name: "Mehl (ca. 200 g)" → "Mehl". */
    private val PARENTHETICAL = Regex("""\s*\([^)]*\)""")

    /**
     * Trailing usage phrase that names a *use*, not a second ingredient — the real item is
     * the head before it: "Butter zum Anbraten" → "Butter", "Salz nach Belieben" → "Salz".
     */
    private val TRAILING_QUALIFIER = Regex("""\s+(zum|zur|nach|für|fürs|to|for)\s+.*$""")

    // Leaked amounts glued into the *name* field (the structured model normally keeps
    // quantity/unit separate, but extraction sometimes leaves "3 Löffel Öl"/"etwas Öl").
    // Stripping the leading amount reduces both to the bare noun so the staple/coverage
    // checks fire. Order: vague quantifier → number(s)/fraction/range → a single unit word.
    private val LEADING_VAGUE =
        Regex("""^(etwas|ein wenig|ein paar|ein bisschen|eine prise|eine handvoll|nach belieben|circa|ca\.?)\s+""")
    private val LEADING_NUMBER =
        Regex("""^(\d+([.,]\d+)?([-–]\d+([.,]\d+)?)?|[½¼¾⅓⅔⅛])\s*""")
    private val LEADING_UNIT = Regex(
        """^(el|tl|g|kg|mg|ml|cl|l|dose|dosen|glas|gläser|becher|prise|prisen|löffel|""" +
            """esslöffel|teelöffel|tasse|tassen|bund|stück|stk\.?|packung|packungen|pkg|pck|""" +
            """scheibe|scheiben|zehe|zehen|kopf|köpfe|blatt|blätter)\s+""",
    )

    /** True if [a] and [b] refer to the same ingredient (see class doc for the layers). */
    fun matches(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (isDistinct(x, y)) return false
        if (x == y) return true
        if (IngredientLexicon.sameSynonym(x, y)) return true
        if (PLURAL_SUFFIXES.any { suf -> isPluralOf(x, y, suf) || isPluralOf(y, x, suf) }) return true
        // Compound-noun head: in German the right-most part is the head ("Weizen-mehl" → mehl).
        // Only conflate when that head is a staple — otherwise distinct products that merely
        // share a suffix ("Kichererbsen"/"Erbsen") would collapse together.
        if (' ' in x || ' ' in y) return false
        return (isCompoundHead(x, y) && IngredientStaples.isStapleWord(y)) ||
            (isCompoundHead(y, x) && IngredientStaples.isStapleWord(x))
    }

    /**
     * True if a pantry stock named [pantry] satisfies a recipe call for [ingredient].
     * Asymmetric: a *staple* generic pantry noun covers an adjective-qualified variety
     * ("Pfeffer" covers "schwarzer Pfeffer"); a non-staple generic ("Bohnen") does not
     * cover "weiße Bohnen". A learned/curated distinction blocks coverage outright.
     */
    fun covers(pantry: String, ingredient: String): Boolean {
        val p = normalize(pantry)
        val i = normalize(ingredient)
        if (p.isEmpty() || i.isEmpty()) return false
        if (isDistinct(p, i)) return false
        if (matches(pantry, ingredient)) return true
        // Generic single-word pantry noun vs an adjective-qualified recipe ingredient: the
        // last whitespace-separated token in German is the head noun. Only a staple head
        // generalizes to its variety.
        if (' ' !in p && ' ' in i) {
            val head = i.substringAfterLast(' ')
            if (isDistinct(p, head)) return false
            return IngredientStaples.isStapleWord(p) && matches(p, head)
        }
        return false
    }

    /** Public, idempotent name normalization — shared with the lexicon/learned-link holders. */
    fun normalizeName(s: String): String = normalize(s)

    // normalize() sits on the meal-planner's hot path — generateWeekBest calls it millions of
    // times per sweep. Regex passes each allocate a Matcher, so memoize: the set of *distinct*
    // ingredient strings is tiny, and millions of calls collapse to a few hundred computations.
    private val normalizeCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Lowercases, strips parentheticals / trailing use-phrases / leaked leading amounts, and
     * folds German cooking-vocab variants ("Soße" ↔ "Sauce", "ß" → "ss") so "Sojasoße" and
     * "Sojasauce" compare equal.
     */
    private fun normalize(s: String): String = normalizeCache.computeIfAbsent(s) { raw ->
        var t = raw.lowercase()
            .replace(PARENTHETICAL, "")
            .replace(TRAILING_QUALIFIER, "")
            .trim()
        t = t.replace(LEADING_VAGUE, "")
        t = t.replace(LEADING_NUMBER, "")
        t = t.replace(LEADING_UNIT, "")
        t.replace("soße", "sauce")
            .replace("ß", "ss")
            .trim()
    }

    private fun isDistinct(x: String, y: String): Boolean =
        LearnedIngredientLinks.isDistinct(x, y) || IngredientLexicon.isCuratedDistinct(x, y)

    private fun isPluralOf(whole: String, stem: String, suf: String): Boolean =
        whole.length - suf.length >= MIN_STEM && whole.endsWith(suf) && whole.dropLast(suf.length) == stem

    /** True if [head] is the right-most component of compound [whole] ("Mehl" in "Weizenmehl"). */
    private fun isCompoundHead(whole: String, head: String): Boolean =
        head.length >= MIN_STEM &&
            whole.length - head.length >= MIN_STEM &&
            whole.endsWith(head)

    /** True if any element of [set] (treated as pantry-side) covers [name] (recipe-side). */
    fun containsLike(set: Collection<String>, name: String): Boolean = set.any { covers(it, name) }
}
