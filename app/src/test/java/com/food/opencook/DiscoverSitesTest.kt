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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoverSitesTest {

    // The boards and the site names live in arrays.xml and are pushed in at startup; a JVM test
    // has no resources, so it seeds the same data itself. Keep in step with values*/arrays.xml.
    private val german = listOf(
        "https://www.chefkoch.de/rezepte/",
        "https://eatsmarter.de/rezepte",
        "https://www.essen-und-trinken.de/rezepte",
    )
    private val english = listOf(
        "https://www.allrecipes.com/recipes/",
        "https://www.bbc.co.uk/food/recipes",
        "https://www.recipetineats.com/recipes/",
    )
    private val french = listOf(
        "https://www.marmiton.org/recettes/",
        "https://www.cuisineaz.com/recettes-p1",
    )
    private val names = mapOf(
        "chefkoch" to "Chefkoch",
        "eatsmarter" to "EAT SMARTER",
        "essen-und-trinken" to "Essen & Trinken",
        "bbcgoodfood" to "BBC Good Food",
        "bbc" to "BBC Food",
        "recipetineats" to "RecipeTin Eats",
        "cuisineaz" to "Cuisine AZ",
    )

    private val savedBoards = DiscoverSites.activeBoards
    private val savedNames = SourceCookbook.activeNames

    @Before fun seed() {
        DiscoverSites.setBoards(mapOf("de" to german, "en" to english, "fr" to french))
        SourceCookbook.setNames(names)
    }

    @After fun restore() {
        DiscoverSites.setBoards(savedBoards.mapValues { (_, v) -> v.map { it.url } })
        SourceCookbook.setNames(savedNames)
    }

    private val allBoards get() = DiscoverSites.defaultsFor("de") +
        DiscoverSites.defaultsFor("en") + DiscoverSites.defaultsFor("fr")

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
        // A site nobody named reads as its own domain label, capitalized.
        assertEquals("Marmiton", DiscoverSite("https://www.marmiton.org/recettes/").label)
        for (site in allBoards) {
            assertEquals(site.url, SourceCookbook.fromUrl(site.url), site.label)
        }
    }

    @Test
    fun tilesAreDistinctWithinEachBoard() {
        for (lang in listOf("de", "en", "fr")) {
            val board = DiscoverSites.defaultsFor(lang)
            val labels = board.map { it.label }
            assertEquals(labels.size, labels.toSet().size)
            val urls = board.map { it.url }
            assertEquals(urls.size, urls.toSet().size)
        }
    }

    /** Each language gets its own board; a language without one falls back to English. */
    @Test
    fun boardFollowsTheContentLanguage() {
        assertEquals(german, DiscoverSites.defaultsFor("de").map { it.url })
        assertEquals(german, DiscoverSites.defaultsFor("DE").map { it.url })
        assertEquals(english, DiscoverSites.defaultsFor("en").map { it.url })
        assertEquals(french, DiscoverSites.defaultsFor("fr").map { it.url })
        assertEquals(english, DiscoverSites.defaultsFor("it").map { it.url })
        assertEquals(english, DiscoverSites.defaultsFor("").map { it.url })
    }

    /** A tile hidden in one language stays a default in the other — never a duplicate. */
    @Test
    fun hidingIsRememberedAcrossBoards() {
        val chefkoch = german.first()
        assertTrue(DiscoverSites.isDefault(chefkoch))
        assertTrue(DiscoverSites.isDefault(english.first()))
        assertTrue(DiscoverSites.isDefault(french.first()))

        val visible = DiscoverSites.visible(setOf(chefkoch), emptyList(), "de")
        assertTrue(visible.none { it.url == chefkoch })
        assertEquals(german.size - 1, visible.size)
    }

    @Test
    fun addedAddressesFollowTheDefaults() {
        val own = "https://example.com/recipes"
        val visible = DiscoverSites.visible(emptySet(), listOf(own), "en")
        assertEquals(english.size + 1, visible.size)
        assertEquals(own, visible.last().url)
        // An address that is already a default must not appear twice.
        val dupe = DiscoverSites.visible(emptySet(), listOf(english.first()), "en")
        assertEquals(english.size, dupe.size)
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
