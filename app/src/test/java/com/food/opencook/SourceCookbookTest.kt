package com.food.opencook

import com.food.opencook.data.recipeimport.SourceCookbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceCookbookTest {

    @Test
    fun knownSitesGetFriendlyNames() {
        assertEquals("Chefkoch", SourceCookbook.fromUrl("https://www.chefkoch.de/rezepte/123/x.html"))
        assertEquals("NDR", SourceCookbook.fromUrl("https://www.ndr.de/ratgeber/kochen/rezepte/x-100.html"))
        assertEquals("Kochbar", SourceCookbook.fromUrl("https://www.kochbar.de/rezept/385471/x.html"))
    }

    @Test
    fun stripsWwwAndSubdomains() {
        assertEquals("Chefkoch", SourceCookbook.fromUrl("https://m.chefkoch.de/rezepte/123/x.html"))
        assertEquals("NDR", SourceCookbook.fromUrl("http://ndr.de/x"))
    }

    @Test
    fun unknownDomainIsCapitalized() {
        assertEquals("Gaumenfreundin", SourceCookbook.fromUrl("https://www.gaumenfreundin.de/rezept/x/"))
        assertEquals("Example", SourceCookbook.fromUrl("https://example.com/recipe"))
    }

    @Test
    fun nullForUnusableInput() {
        assertNull(SourceCookbook.fromUrl(null))
        assertNull(SourceCookbook.fromUrl(""))
        assertNull(SourceCookbook.fromUrl("not a url"))
        assertNull(SourceCookbook.fromUrl("http://localhost/x"))   // no dot → bare host
        assertNull(SourceCookbook.fromUrl("http://192.168.1.5:8000/x")) // IP address
    }
}
