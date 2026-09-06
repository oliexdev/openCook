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

import com.food.opencook.data.recipeimport.JsonLdExtractor
import com.food.opencook.ui.discover.PageHtml
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageHtmlTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun unwrapsTheJsonValueWebViewReportsBack() {
        assertEquals("hello", PageHtml.unwrapJsString("\"hello\""))
        // Real payloads arrive escaped: quotes, newlines and the NUL separator.
        assertEquals(
            "{\"@type\":\"Recipe\"}\u0000{}",
            PageHtml.unwrapJsString("\"{\\\"@type\\\":\\\"Recipe\\\"}\\u0000{}\""),
        )
    }

    @Test
    fun nullForNothingUsable() {
        assertNull(PageHtml.unwrapJsString(null))
        assertNull(PageHtml.unwrapJsString("null"))     // the literal JS null
        assertNull(PageHtml.unwrapJsString("\"\""))     // page had no ld+json blocks
        assertNull(PageHtml.unwrapJsString("\"   \""))
        assertNull(PageHtml.unwrapJsString("not json"))
    }

    @Test
    fun recognizesARecipePage() {
        assertTrue(PageHtml.looksLikeRecipe("""{"@type":"Recipe","name":"Lasagne"}"""))
        assertTrue(PageHtml.looksLikeRecipe("""{"@type":["Recipe","NewsArticle"]}"""))
        assertFalse(PageHtml.looksLikeRecipe("""{"@type":"ItemList"}"""))   // a listing page
        assertFalse(PageHtml.looksLikeRecipe(null))
    }

    /** The fragment exists for one reason: the existing extractor must read it unchanged. */
    @Test
    fun fragmentFeedsTheExistingJsonLdExtractor() {
        val blocks = """{"@type":"Organization","name":"Site"}""" + "\u0000" +
            """{"@type":"Recipe","name":"Lasagne","recipeIngredient":["500 g Hackfleisch"]}"""

        val fragment = PageHtml.toHtmlFragment(blocks)
        assertEquals(2, Regex("<script").findAll(fragment).count())

        val recipe = JsonLdExtractor.extractFirstRecipe(fragment, json)
        assertNotNull(recipe)
        assertEquals("Lasagne", recipe!!.dto.name)
    }

    @Test
    fun fragmentSkipsEmptyBlocks() {
        assertEquals("", PageHtml.toHtmlFragment("\u0000   \u0000"))
    }
}
