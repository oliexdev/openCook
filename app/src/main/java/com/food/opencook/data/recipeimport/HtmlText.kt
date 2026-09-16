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

package com.food.opencook.data.recipeimport

/**
 * Plain text out of the HTML that recipe sites leave inside their JSON-LD.
 *
 * schema.org asks for text, but sites hand their CMS fragment through unchanged, so a
 * HelloFresh step arrives as `Zwiebel schälen.<br>Mit <b>Salz &amp; Pfeffer</b> würzen.`
 * Without this the markup lands verbatim in the recipe.
 *
 * Two deliberate decisions:
 *
 *  - **Only known elements are markup.** A generic `<[^>]+>` would also eat `<180 °C>` or
 *    `<wichtig>` — and this runs on our own backups too ([RecipeImportParser] is the restore
 *    path), where that is the user's own text.
 *  - **Block ends become newlines**, inline elements simply vanish. The importer reads a
 *    newline as a step boundary, so `<br>`/`</li>`/`</p>` end up as the separate numbered
 *    steps the site drew, instead of one run-on paragraph.
 */
object HtmlText {

    /** Elements we accept as markup — what recipe CMSes actually emit. */
    private const val ELEMENTS =
        "a|abbr|article|aside|b|big|blockquote|br|center|cite|code|dd|div|dl|dt|em|figcaption|" +
            "figure|font|h[1-6]|hr|i|img|label|li|mark|nobr|ol|p|pre|q|s|section|small|span|" +
            "strong|sub|sup|table|tbody|td|tfoot|th|thead|time|tr|u|ul|wbr"

    /** Markup that ends a block — what a cook reads as "next line". */
    private val BREAK = Regex(
        """<br\s*/?>|</(p|div|li|tr|ul|ol|dd|dt|h[1-6]|table|pre|blockquote|section|article|""" +
            """figure|figcaption)\s*>""",
        RegexOption.IGNORE_CASE,
    )
    private val TAG = Regex("""</?($ELEMENTS)(\s[^<>]*)?\s*/?>""", RegexOption.IGNORE_CASE)

    /** A whitespace run around a line break collapses into that one break. */
    private val AROUND_BREAK = Regex("""\s*\n\s*""")

    private val ENTITY = Regex("""&(#\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,9});""")

    /** Named entities are case-sensitive (`&Auml;` is not `&auml;`), so no normalising here. */
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "shy" to "­",
        "hellip" to "…", "ndash" to "–", "mdash" to "—", "minus" to "−",
        "deg" to "°", "middot" to "·", "times" to "×", "euro" to "€",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
        "sup2" to "²", "sup3" to "³",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚",
        "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
        "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "szlig" to "ß",
        "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü",
    )

    /**
     * [s] without markup: block ends as newlines, entities decoded, invisible characters
     * normalised. Text that holds none of that is returned untouched (the common case —
     * our own backups and well-behaved exporters).
     */
    fun toPlainText(s: String): String {
        if (s.none { it == '<' || it == '&' || it == ' ' || it == '­' }) return s
        return unescape(s.replace(BREAK, "\n").replace(TAG, ""))
            // A non-breaking space is not `\s` to Java's regex, so IngredientLineParser would
            // read "200 g Mehl" as one token; a soft hyphen is invisible but defeats every
            // later name match. Both become what the cook sees.
            .replace(' ', ' ')
            .replace("­", "")
            .replace(AROUND_BREAK, "\n")
            .trim()
    }

    /**
     * Decodes HTML entities. Runs after tag removal so an escaped `&lt;b&gt;` survives as the
     * text it is, and in a single pass so `&amp;quot;` decodes to `&quot;`, not to a quote.
     * Also used on a whole JSON-LD block by [JsonLdExtractor], hence the control-character
     * guard below.
     */
    fun unescape(s: String): String {
        if ('&' !in s) return s
        return ENTITY.replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x", ignoreCase = true) -> codePoint(body.drop(2).toIntOrNull(16))
                body.startsWith("#") -> codePoint(body.drop(1).toIntOrNull())
                else -> NAMED[body]
            } ?: m.value // an entity we don't know stays as written rather than disappearing
        }
    }

    /** Anything below U+0020 stays written out: decoding `&#10;` inside a JSON-LD block would
     *  tear the JSON string it sits in, and no recipe needs a control character. */
    private fun codePoint(cp: Int?): String? =
        if (cp == null || cp < 0x20 || cp in 0xD800..0xDFFF || !Character.isValidCodePoint(cp)) null
        else String(Character.toChars(cp))
}
