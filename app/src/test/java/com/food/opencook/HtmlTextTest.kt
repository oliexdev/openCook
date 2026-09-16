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

import com.food.opencook.data.recipeimport.HtmlText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HtmlTextTest {

    @Test
    fun plainTextIsReturnedUnchanged() {
        val s = "Zwiebel schälen und fein würfeln."
        assertSame(s, HtmlText.toPlainText(s))
    }

    @Test
    fun breaksBecomeLines_inlineMarkupDisappears() {
        assertEquals(
            "Zwiebel schälen.\nMit Salz & Pfeffer würzen.",
            HtmlText.toPlainText("Zwiebel schälen.<br>Mit <b>Salz &amp; Pfeffer</b> würzen."),
        )
        // Upper case and the self-closing spellings mean the same break.
        assertEquals(
            "Gemüse würfeln.\nAnbraten.",
            HtmlText.toPlainText("<BR/>Gemüse würfeln.<BR />Anbraten."),
        )
        // Paragraphs and list items are breaks too — without them the sentences glue together.
        assertEquals("Ofen vorheizen.\nTeig kneten.", HtmlText.toPlainText("<p>Ofen vorheizen.</p><p>Teig kneten.</p>"))
        assertEquals("Eins\nZwei", HtmlText.toPlainText("<ul><li>Eins</li>\n  <li>Zwei</li></ul>"))
    }

    @Test
    fun attributesAndUnknownEntitiesSurvive() {
        assertEquals("Siehe Tipp.", HtmlText.toPlainText("""Siehe <a href="/tipp" class="x">Tipp</a>."""))
        assertEquals("AT&T;", HtmlText.toPlainText("AT&T;")) // a bare "&T;" is no entity
        assertEquals("100 &unknown; Stück", HtmlText.toPlainText("100 &unknown; Stück"))
    }

    @Test
    fun entitiesDecodeInOnePass() {
        assertEquals("Salz & Pfeffer", HtmlText.toPlainText("Salz &amp; Pfeffer"))
        assertEquals("""&quot;""", HtmlText.toPlainText("&amp;quot;")) // not a quote
        assertEquals("<b> ist Markup", HtmlText.toPlainText("&lt;b&gt; ist Markup"))
        assertEquals("Anna's Käse bei 180 °C – ½ Std.", HtmlText.toPlainText("Anna&#39;s K&auml;se bei 180 &deg;C &ndash; &frac12; Std."))
        assertEquals("'x'", HtmlText.toPlainText("&#x27;x&#X27;"))
    }

    @Test
    fun invisibleCharactersBecomeWhatTheCookSees() {
        // A non-breaking space is not `\s` to Java's regex — IngredientLineParser needs a plain one.
        assertEquals("200 g Mehl", HtmlText.toPlainText("200&nbsp;g Mehl"))
        assertEquals("200 g Mehl", HtmlText.toPlainText("200 g Mehl"))
        assertEquals("Zwiebel", HtmlText.toPlainText("Zwie&shy;bel"))
    }

    @Test
    fun angleBracketsThatAreNotMarkupStay() {
        // Our own backups run through this path, where these are the user's own words.
        assertEquals("Backofen <180 °C> vorheizen", HtmlText.toPlainText("Backofen <180 °C> vorheizen"))
        assertEquals("Würfel < 1 cm schneiden", HtmlText.toPlainText("Würfel < 1 cm schneiden"))
    }

    @Test
    fun controlEntitiesAreLeftAlone() {
        // JsonLdExtractor runs unescape over a whole JSON-LD block; a decoded newline would
        // tear the JSON string it sits in.
        assertEquals("a&#10;b", HtmlText.unescape("a&#10;b"))
        assertEquals("""{"name":"Suppe & Co"}""", HtmlText.unescape("""{&quot;name&quot;:&quot;Suppe &amp; Co&quot;}"""))
    }
}
