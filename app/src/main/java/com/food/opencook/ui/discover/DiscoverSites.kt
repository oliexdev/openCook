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

import com.food.opencook.data.recipeimport.SourceCookbook
import java.net.URI

/**
 * One starting point for the in-app browser. Only the [url] is stored: the display name comes
 * from [SourceCookbook], the very same mapping the importer uses to file a saved recipe under
 * its site — so the tile a recipe came from and the cookbook it lands in can never drift apart.
 *
 * Deliberately no logos: these are other people's trademarks, and naming a site to point at it
 * is a different thing from dressing the screen up as if we were affiliated with it.
 */
data class DiscoverSite(val url: String) {

    /** "Chefkoch", "EAT SMARTER", … — falls back to the host if the mapping ever fails. */
    val label: String get() = SourceCookbook.fromUrl(url) ?: host

    /** "www.chefkoch.de" — shown small under the label so the destination is never a surprise. */
    val host: String get() = runCatching { URI(url).host }.getOrNull().orEmpty()
}

/**
 * The curated starting points, per language. These are plain links: tapping one opens that
 * site's own recipe section in the in-app browser, where the user browses and picks. openCook
 * never reads the listing pages itself — see the plan's legal note; the app imports exactly the
 * page a person chose to open.
 *
 * The addresses themselves live in `discover_sites` in `values-<lang>/arrays.xml`, so adding a
 * language's board is a translation, not a code change. Every entry was checked to answer with
 * 200; keep it that way when editing.
 */
object DiscoverSites {

    /**
     * language code -> its starting points, filled from `discover_sites` in each
     * `values-<lang>/arrays.xml` by `LocalizedLists` at startup. A new language contributes a
     * board by adding that array — there is nothing to change here.
     */
    @Volatile
    private var boards: Map<String, List<DiscoverSite>> = emptyMap()

    /** The board a household falls back to: the app's own default language. */
    private const val FALLBACK = "en"

    /** Replace the boards (called by `LocalizedLists`). Ignores an empty load so a resource
     *  hiccup leaves the previous boards standing rather than emptying the screen. */
    fun setBoards(byLanguage: Map<String, List<String>>) {
        if (byLanguage.isNotEmpty()) {
            boards = byLanguage.mapValues { (_, urls) -> urls.map(::DiscoverSite) }
        }
    }

    /** Active boards — exposed so tests can snapshot and restore around [setBoards]. */
    val activeBoards: Map<String, List<DiscoverSite>> get() = boards

    /**
     * The board a household starts with, by the language its recipes are in — a board full of
     * German sites is no help to someone cooking in English. Falls back to English, matching
     * the app's own default language, for a language that ships no board of its own.
     */
    fun defaultsFor(language: String): List<DiscoverSite> =
        boards[language.lowercase()] ?: boards[FALLBACK].orEmpty()

    /** True for a tile that ships with the app — those are hidden when removed, not deleted.
     *  Checked across **all** languages, so switching language never turns a hidden tile into a
     *  second copy of itself. */
    fun isDefault(url: String): Boolean = boards.values.any { board -> board.any { it.url == url } }

    /**
     * The tiles to show: the built-in ones for [language] that the user kept, plus the addresses
     * they added, in that order. Pure, so the arrangement is testable without a DataStore.
     */
    fun visible(hidden: Set<String>, custom: List<String>, language: String): List<DiscoverSite> =
        (defaultsFor(language).map { it.url } + custom)
            .distinct()
            .filterNot { it in hidden }
            .map(::DiscoverSite)

    /**
     * Turns what someone typed into the "other address" field into a loadable URL:
     * "chefkoch.de/rezepte" → "https://chefkoch.de/rezepte". Null when it can't be one.
     */
    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return null
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        val host = runCatching { URI(withScheme).host }.getOrNull()
        return withScheme.takeIf { !host.isNullOrBlank() && host.contains('.') }
    }
}
