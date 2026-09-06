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

package com.food.opencook

import com.food.opencook.data.recipeimport.SourceCookbook
import com.food.opencook.ui.discover.DiscoverSite
import com.food.opencook.ui.discover.DiscoverSites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverSitesTest {

    private val allBoards = DiscoverSites.GERMAN + DiscoverSites.ENGLISH

    @Test
    fun everyTileIsAnHttpsUrlWithAHost() {
        for (site in allBoards) {
            assertTrue(site.url, site.url.startsWith("https://"))
            assertTrue(site.url, site.host.contains('.'))
        }
    }

    /** The tile's name must be the cookbook the import files the recipe under — same mapping. */
    @Test
    fun tileLabelMatchesTheCookbookTheImportWillUse() {
        assertEquals("Chefkoch", DiscoverSite("https://www.chefkoch.de/rezepte/").label)
        assertEquals("EAT SMARTER", DiscoverSite("https://eatsmarter.de/rezepte").label)
        assertEquals("Essen & Trinken", DiscoverSite("https://www.essen-und-trinken.de/rezepte").label)
        // Not a tile any more, but the mapping still has to name a shared link properly.
        assertEquals("BBC Good Food", DiscoverSite("https://www.bbcgoodfood.com/recipes").label)
        // A country suffix must not become the name: bbc.co.uk is "BBC Food", never "Co".
        assertEquals("BBC Food", DiscoverSite("https://www.bbc.co.uk/food/recipes").label)
        assertEquals("RecipeTin Eats", DiscoverSite("https://www.recipetineats.com/recipes/").label)
        for (site in allBoards) {
            assertEquals(site.url, SourceCookbook.fromUrl(site.url), site.label)
        }
    }

    @Test
    fun tilesAreDistinctWithinEachBoard() {
        for (board in listOf(DiscoverSites.GERMAN, DiscoverSites.ENGLISH)) {
            val labels = board.map { it.label }
            assertEquals(labels.size, labels.toSet().size)
            val urls = board.map { it.url }
            assertEquals(urls.size, urls.toSet().size)
        }
    }

    /** German households get German sites; everyone else the English board. */
    @Test
    fun boardFollowsTheContentLanguage() {
        assertEquals(DiscoverSites.GERMAN, DiscoverSites.defaultsFor("de"))
        assertEquals(DiscoverSites.GERMAN, DiscoverSites.defaultsFor("DE"))
        assertEquals(DiscoverSites.ENGLISH, DiscoverSites.defaultsFor("en"))
        assertEquals(DiscoverSites.ENGLISH, DiscoverSites.defaultsFor("fr"))
        assertEquals(DiscoverSites.ENGLISH, DiscoverSites.defaultsFor(""))
    }

    /** A tile hidden in one language stays a default in the other — never a duplicate. */
    @Test
    fun hidingIsRememberedAcrossBoards() {
        val chefkoch = DiscoverSites.GERMAN.first().url
        assertTrue(DiscoverSites.isDefault(chefkoch))
        assertTrue(DiscoverSites.isDefault(DiscoverSites.ENGLISH.first().url))

        val visible = DiscoverSites.visible(setOf(chefkoch), emptyList(), "de")
        assertTrue(visible.none { it.url == chefkoch })
        assertEquals(DiscoverSites.GERMAN.size - 1, visible.size)
    }

    @Test
    fun addedAddressesFollowTheDefaults() {
        val own = "https://example.com/recipes"
        val visible = DiscoverSites.visible(emptySet(), listOf(own), "en")
        assertEquals(DiscoverSites.ENGLISH.size + 1, visible.size)
        assertEquals(own, visible.last().url)
        // An address that is already a default must not appear twice.
        val dupe = DiscoverSites.visible(emptySet(), listOf(DiscoverSites.ENGLISH.first().url), "en")
        assertEquals(DiscoverSites.ENGLISH.size, dupe.size)
    }

    @Test
    fun normalizeAddsTheMissingScheme() {
        assertEquals("https://chefkoch.de/rezepte", DiscoverSites.normalizeUrl("chefkoch.de/rezepte"))
        assertEquals("https://www.lecker.de/", DiscoverSites.normalizeUrl("  https://www.lecker.de/  "))
        assertEquals("http://nas.local.example/x", DiscoverSites.normalizeUrl("http://nas.local.example/x"))
    }

    @Test
    fun normalizeRejectsWhatIsNotAnAddress() {
        assertNull(DiscoverSites.normalizeUrl(""))
        assertNull(DiscoverSites.normalizeUrl("   "))
        assertNull(DiscoverSites.normalizeUrl("lasagne mit spinat"))  // a search phrase, not a URL
        assertNull(DiscoverSites.normalizeUrl("localhost"))           // no dot → no host
    }
}
