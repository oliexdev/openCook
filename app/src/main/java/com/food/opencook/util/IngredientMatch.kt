package com.food.opencook.util

/**
 * Plural/singular-aware ingredient-name matching for pantry coverage, ingredient reuse
 * (shoppability), the "missing items" badge and shopping-list pantry skipping.
 *
 * Purely a comparison **predicate** — it never rewrites stored names; "Zwiebeln" and
 * "Zwiebel" simply compare equal. Handles German plural suffixes AND single-word
 * compound-noun heads ("Mehl" ↔ "Weizenmehl").
 *
 * For pantry-vs-recipe coverage use [covers] (asymmetric): a generic pantry noun
 * covers a more specific recipe ingredient ("Pfeffer" covers "schwarzer Pfeffer"),
 * but not the other way around — being specific in the pantry doesn't satisfy a
 * generic recipe request. Use [matches] (symmetric) for "are these the same item?".
 */
object IngredientMatch {

    private val PLURAL_SUFFIXES = listOf("en", "n", "e", "s")
    private const val MIN_STEM = 3

    /** True if [a] and [b] refer to the same ingredient up to plural form or compound-noun head. */
    fun matches(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        if (PLURAL_SUFFIXES.any { suf -> isPluralOf(x, y, suf) || isPluralOf(y, x, suf) }) return true
        // Compound-noun head: in German, the right-most part is the head ("Weizen-mehl" → mehl).
        // Symmetric matches() only conflates single-word forms; multi-word adjective phrases
        // (e.g. "rote Paprika") stay distinct here. Asymmetric coverage lives in [covers].
        if (' ' in x || ' ' in y) return false
        return isCompoundHead(x, y) || isCompoundHead(y, x)
    }

    /**
     * Lowercases and folds common German cooking-vocab variants:
     *   - "Soße" ↔ "Sauce" (same word, different spelling tradition)
     *   - "ß" → "ss" (Swiss / post-1996 orthography)
     * so "Sojasoße" and "Sojasauce" compare equal.
     */
    private fun normalize(s: String): String =
        s.lowercase().trim()
            .replace("soße", "sauce")
            .replace("ß", "ss")

    /**
     * True if a pantry stock named [pantry] satisfies a recipe call for [ingredient].
     * Asymmetric on purpose: pantry "Pfeffer" covers "schwarzer Pfeffer" (you have
     * pepper, no need to buy a more specific kind), but pantry "schwarzer Pfeffer"
     * does NOT cover ingredient "Pfeffer" — the recipe might want a different variety,
     * so leave it on the shopping list and let the user judge.
     */
    fun covers(pantry: String, ingredient: String): Boolean {
        if (matches(pantry, ingredient)) return true
        val p = normalize(pantry)
        val i = normalize(ingredient)
        if (p.isEmpty() || i.isEmpty()) return false
        // Generic pantry noun covers an adjective-qualified recipe ingredient: the
        // last whitespace-separated token in German is the head noun, so we only
        // accept that direction.
        if (' ' !in p && ' ' in i) {
            val head = i.substringAfterLast(' ')
            return matches(p, head)
        }
        return false
    }

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
