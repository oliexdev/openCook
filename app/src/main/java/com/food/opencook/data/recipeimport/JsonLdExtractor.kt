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

import kotlinx.serialization.json.Json

/**
 * Pulls the first schema.org/Recipe out of a web page's HTML by reading its
 * `<script type="application/ld+json">` blocks — the native counterpart to the browser
 * extension's extractor. Pure/offline (the caller fetches the HTML), so it's unit-testable.
 *
 * Each block is handed to the existing [RecipeBundle]/[RecipeImportParser] path, which walks
 * `@graph`/arrays, tolerates non-Recipe nodes, and resolves the image ref. Malformed blocks
 * are skipped; a block that fails to parse is retried once with basic HTML entities decoded
 * (some sites HTML-escape the JSON inside the script tag).
 */
object JsonLdExtractor {

    private val JSON_LD_BLOCK = Regex(
        """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun extractFirstRecipe(html: String, json: Json): ImportedRecipe? {
        for (raw in JSON_LD_BLOCK.findAll(html).map { it.groupValues[1].trim() }) {
            for (candidate in listOf(raw, unescapeBasicEntities(raw))) {
                val imp = runCatching { RecipeBundle.read(candidate.encodeToByteArray(), json) }
                    .getOrDefault(emptyList())
                    .firstOrNull()
                if (imp != null) return imp
            }
        }
        return null
    }

    private fun unescapeBasicEntities(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#34;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
}
