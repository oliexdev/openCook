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

import java.net.URI
import java.util.Locale

/**
 * Derives a friendly "cookbook" name from a recipe's source page URL, so web imports
 * (browser extension + share) group under their site in the cookbook view. Used only as a
 * fallback: a real schema.org `isPartOf` cookbook always takes precedence (see callers).
 *
 * Pure/offline, so it's unit-testable.
 */
object SourceCookbook {

    // Nice casing for common German recipe sites; everything else is capitalized generically.
    private val KNOWN = mapOf(
        "chefkoch" to "Chefkoch",
        "ndr" to "NDR",
        "wdr" to "WDR",
        "kochbar" to "Kochbar",
        "lecker" to "Lecker",
        "eatsmarter" to "EAT SMARTER",
        "essen-und-trinken" to "Essen & Trinken",
        "gaumenfreundin" to "Gaumenfreundin",
        "kitchenstories" to "Kitchen Stories",
        "springlane" to "Springlane",
        "brigitte" to "Brigitte",
    )

    /** Friendly cookbook name from a page URL, or null if it has no usable host. */
    fun fromUrl(url: String?): String? {
        val host = url?.let { runCatching { URI(it).host }.getOrNull() }
            ?.lowercase(Locale.ROOT)
            ?: return null
        val label = mainLabel(host) ?: return null
        return KNOWN[label] ?: label.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /** The registrable site label (second-level domain), stripping a leading "www." —
     *  "www.chefkoch.de" → "chefkoch", "m.ndr.de" → "ndr". Null for bare hosts / IPs. */
    private fun mainLabel(host: String): String? {
        val parts = host.removePrefix("www.").split(".").filter { it.isNotEmpty() }
        if (parts.size < 2) return null                          // needs at least name.tld
        if (parts.all { it.toIntOrNull() != null }) return null  // looks like an IP address
        return parts[parts.size - 2].takeIf { it.isNotBlank() }
    }
}
