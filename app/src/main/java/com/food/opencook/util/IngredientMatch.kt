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

    private const val MIN_STEM = 3

    /** Quantity / prep note glued onto a name: "Mehl (ca. 200 g)" → "Mehl". */
    private val PARENTHETICAL = Regex("""\s*\([^)]*\)""")

    /** A leaked amount at the front: "3 Löffel Öl" → "Löffel Öl", "1/2 TL Salz" → "TL Salz". */
    private val LEADING_NUMBER =
        Regex("""^(\d+([.,]\d+)?([-–]\d+([.,]\d+)?)?|[½¼¾⅓⅔⅛])\s*""")

    /**
     * The language-dependent words this matcher needs. All four are plain word lists, so a new
     * language is added in `arrays.xml` — see `LocalizedLists`, which pushes in the union over
     * every bundled content language.
     *
     * @param leadingNoise measure and vague-amount words that leak into the *name* field
     *   ("3 Löffel Öl", "etwas Öl"); stripped before **and** after the number, so one list
     *   covers both positions.
     * @param usePhrases words that open a trailing *use* phrase rather than naming a second
     *   ingredient: "Butter zum Anbraten" → "Butter", "huile pour la friture" → "huile".
     * @param pluralSuffixes suffixes that relate a plural to its singular ("Tomaten"/"Tomate").
     * @param headConnectors words that mark the *modifier* of a head-initial compound, so the
     *   head is the first token: "huile d\'olive" → "huile". German and English put the head
     *   last and contribute nothing here — an empty list keeps exactly the old rule.
     */
    data class Vocabulary(
        val leadingNoise: List<String> = emptyList(),
        val usePhrases: List<String> = emptyList(),
        val pluralSuffixes: List<String> = emptyList(),
        val headConnectors: List<String> = emptyList(),
    )

    /** German+English fallback, so unit tests and a not-yet-initialized process behave as before. */
    private val DEFAULT_VOCABULARY_DE_EN = Vocabulary(
        leadingNoise = listOf(
            "etwas", "ein wenig", "ein paar", "ein bisschen", "eine prise", "eine handvoll",
            "nach belieben", "circa", "ca.", "ca", "about", "approx.", "approx",
            "el", "tl", "g", "kg", "mg", "ml", "cl", "l", "dose", "dosen", "glas", "gläser",
            "becher", "prise", "prisen", "löffel", "esslöffel", "teelöffel", "tasse", "tassen",
            "bund", "stück", "stk.", "stk", "packung", "packungen", "pkg", "pck",
            "scheibe", "scheiben", "zehe", "zehen", "kopf", "köpfe", "blatt", "blätter",
            "tbsp", "tsp", "cup", "cups", "oz", "lb", "clove", "cloves", "pinch", "slice", "slices",
        ),
        usePhrases = listOf("zum", "zur", "nach", "für", "fürs", "to", "for"),
        pluralSuffixes = listOf("en", "n", "e", "s"),
        headConnectors = emptyList(),
    )

    @Volatile
    private var vocabulary: Vocabulary = DEFAULT_VOCABULARY_DE_EN

    @Volatile
    private var leadingNoiseRe: Regex? = leadingRegex(DEFAULT_VOCABULARY_DE_EN.leadingNoise)

    @Volatile
    private var usePhraseRe: Regex? = trailingRegex(DEFAULT_VOCABULARY_DE_EN.usePhrases)

    /**
     * Replace the language vocabulary (called by `LocalizedLists`). Clears the normalization
     * memo, because every cached entry was produced by the previous word lists.
     */
    fun setVocabulary(v: Vocabulary) {
        vocabulary = v
        leadingNoiseRe = leadingRegex(v.leadingNoise)
        usePhraseRe = trailingRegex(v.usePhrases)
        normalizeCache.clear()
    }

    /** Active vocabulary — exposed so tests can snapshot and restore around [setVocabulary]. */
    val activeVocabulary: Vocabulary get() = vocabulary

    /** Longest word first, so "eine prise" wins over "eine"; entries are literal, not patterns. */
    private fun alternation(words: Collection<String>): String =
        words.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

    private fun leadingRegex(words: Collection<String>): Regex? =
        alternation(words).takeIf { it.isNotEmpty() }?.let { Regex("^($it)\\s+") }

    private fun trailingRegex(words: Collection<String>): Regex? =
        alternation(words).takeIf { it.isNotEmpty() }?.let { Regex("\\s+($it)\\s+.*$") }

    /** True if [a] and [b] refer to the same ingredient (see class doc for the layers). */
    fun matches(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (isDistinct(x, y)) return false
        if (x == y) return true
        if (IngredientLexicon.sameSynonym(x, y)) return true
        if (vocabulary.pluralSuffixes.any { suf -> isPluralOf(x, y, suf) || isPluralOf(y, x, suf) }) return true
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
        // Generic single-word pantry noun vs a qualified recipe ingredient. Only a staple head
        // generalizes to its variety.
        if (' ' !in p && ' ' in i) {
            val head = headOf(i)
            if (isDistinct(p, head)) return false
            return IngredientStaples.isStapleWord(p) && matches(p, head)
        }
        return false
    }

    /**
     * The head noun of a multi-word ingredient. German and English put it last ("schwarzer
     * Pfeffer", "black pepper"). Romance languages put it first and mark the modifier with a
     * connector ("huile d\'olive", "aceite de oliva") — so the head is the first token exactly
     * when the second one is such a connector. Because that list is per-language data and
     * German/English contribute none, this cannot make "sugar snap peas" look like sugar.
     */
    private fun headOf(name: String): String {
        val tokens = name.trim().split(Regex("\\s+"))
        if (tokens.size >= 2 && isHeadConnector(tokens[1])) return tokens[0]
        return tokens.last()
    }

    /** A connector ending in an apostrophe binds to its noun ("d\'olive"), so match the prefix. */
    private fun isHeadConnector(token: String): Boolean = vocabulary.headConnectors.any { c ->
        if (c.endsWith("'")) token.startsWith(c) else token == c
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
        var t = raw.lowercase().replace(PARENTHETICAL, "")
        usePhraseRe?.let { t = t.replace(it, "") }
        t = t.trim()
        // The noise list runs on both sides of the number, so "etwas Öl" and "3 Löffel Öl"
        // both reduce to the bare noun without needing two separate word lists.
        leadingNoiseRe?.let { t = t.replace(it, "") }
        t = t.replace(LEADING_NUMBER, "")
        leadingNoiseRe?.let { t = t.replace(it, "") }
        t.replace("soße", "sauce")
            .replace("ß", "ss")
            // Typographic apostrophe ’ vs the ASCII one — both spellings of "huile d'olive"
            // must compare equal, or a staple written one way misses the pantry row written the other.
            .replace('’', '\'')
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
