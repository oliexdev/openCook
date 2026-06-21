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
