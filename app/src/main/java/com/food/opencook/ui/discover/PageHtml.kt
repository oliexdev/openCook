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

package com.food.opencook.ui.discover

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The pure half of the in-app browser's import: everything that can be reasoned about (and
 * unit-tested) without a WebView.
 *
 * The browser never hands the whole page over. [COLLECT_JSON_LD] pulls just the
 * `application/ld+json` blocks out of the *rendered* DOM — a few KB instead of megabytes, and
 * exactly the structured data the site publishes for search engines. [toHtmlFragment] wraps them
 * back into script tags so the existing [com.food.opencook.data.recipeimport.JsonLdExtractor]
 * reads them unchanged.
 */
object PageHtml {

    /** NUL joins the blocks — it cannot occur inside JSON text, so the split is unambiguous. */
    private const val SEPARATOR = '\u0000'

    /**
     * JavaScript evaluated in the open page: returns its ld+json blocks joined by NUL, or an
     * empty string. Kept defensive — a page that throws here must not break the toolbar.
     */
    const val COLLECT_JSON_LD = """
        (function () {
          try {
            var out = [];
            var nodes = document.querySelectorAll('script[type="application/ld+json"]');
            for (var i = 0; i < nodes.length; i++) { out.push(nodes[i].textContent || ''); }
            return out.join(String.fromCharCode(0));
          } catch (e) { return ''; }
        })()
    """

    /**
     * `WebView.evaluateJavascript` reports its result as a **JSON value**, so a string arrives
     * quoted and escaped, and "no value" arrives as the literal `null`. Returns the real string,
     * or null when there is nothing usable.
     */
    fun unwrapJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        val decoded = runCatching { Json.parseToJsonElement(raw).jsonPrimitive.contentOrNull }
            .getOrNull()
            ?: return null
        return decoded.takeIf { it.isNotBlank() }
    }

    /** True when the collected blocks announce a schema.org/Recipe — drives the import button. */
    fun looksLikeRecipe(blocks: String?): Boolean = blocks?.contains("\"Recipe\"") == true

    /** The collected blocks as an HTML fragment the JSON-LD extractor understands. */
    fun toHtmlFragment(blocks: String): String =
        blocks.split(SEPARATOR)
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<script type=\"application/ld+json\">$it</script>" }
}
