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
 * The curated starting points. These are plain links: tapping one opens that site's own recipe
 * section in the in-app browser, where the user browses and picks. openCook never reads the
 * listing pages itself — see the plan's legal note; the app imports exactly the page a person
 * chose to open.
 *
 * Every entry was checked to answer with 200; keep it that way when editing.
 */
object DiscoverSites {

    /**
     * German-language starting points, shown when the household reads its recipes in German.
     */
    val GERMAN: List<DiscoverSite> = listOf(
        "https://www.chefkoch.de/rezepte/",
        "https://www.kochbar.de/rezepte/",
        "https://www.lecker.de/rezepte",
        "https://eatsmarter.de/rezepte",
        "https://www.essen-und-trinken.de/rezepte",
        "https://www.gaumenfreundin.de/rezepte/",
        "https://www.kitchenstories.com/de/rezepte",
        "https://www.springlane.de/magazin/",
        "https://www.brigitte.de/rezepte/",
        "https://www.ndr.de/ratgeber/kochen/",
    ).map(::DiscoverSite)

    /**
     * English-language starting points — the same idea for everyone else. Three of them
     * (Allrecipes, Serious Eats, Simply Recipes) refuse automated requests, so
     * they could not be checked from a script; they are here because they are among the
     * best-known recipe sites there are, and a tile that does not work is one tap from gone.
     *
     * The number in the Serious Eats address is that site's content id — stable, not a token,
     * but it is the one entry here that would silently point at nothing if they reshuffle their
     * sections. Everything else is addressed by path.
     */
    val ENGLISH: List<DiscoverSite> = listOf(
        "https://www.allrecipes.com/recipes/",
        "https://www.bbc.co.uk/food/recipes",
        "https://www.seriouseats.com/all-recipes-5117985",
        "https://www.simplyrecipes.com/",
        "https://www.epicurious.com/recipes-menus",
        "https://www.delish.com/cooking/recipe-ideas/",
        "https://www.jamieoliver.com/recipes/",
        "https://www.budgetbytes.com/category/recipes/",
        "https://www.tasteofhome.com/recipes/",
        "https://www.recipetineats.com/recipes/",
    ).map(::DiscoverSite)

    /**
     * The board a household starts with, by the language its recipes are in — a board full of
     * German sites is no help to someone cooking in English. English is the fallback, matching
     * the app's own default language.
     */
    fun defaultsFor(language: String): List<DiscoverSite> =
        if (language.equals("de", ignoreCase = true)) GERMAN else ENGLISH

    /** True for a tile that ships with the app — those are hidden when removed, not deleted.
     *  Checked across **all** languages, so switching language never turns a hidden tile into a
     *  second copy of itself. */
    fun isDefault(url: String): Boolean =
        GERMAN.any { it.url == url } || ENGLISH.any { it.url == url }

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
