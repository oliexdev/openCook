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

import com.food.opencook.data.image.ImageSniff
import com.food.opencook.data.recipeimport.RecipeImportParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The photo of a web-imported recipe. Both halves are guarded here because the failure is
 * silent: a wrong address answers 200 and the recipe ends up carrying an HTML file as its image.
 */
class RecipeImageRefTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Chefkoch's shape: the recipe only points at the photo, a sibling node holds the URL. */
    @Test
    fun followsAnImageReferenceIntoTheGraph() {
        val doc = """
            {"@context":"https://schema.org","@graph":[
              {"@type":"Recipe","@id":"https://x.de/r.html#recipe","name":"Riesling-Huhn",
               "recipeIngredient":["1 Huhn"],
               "image":{"@id":"https://x.de/r.html#primaryimage"}},
              {"@type":"ImageObject","@id":"https://x.de/r.html#primaryimage",
               "url":"https://img.x-cdn.de/riesling-huhn.jpg"},
              {"@type":"WebPage","@id":"https://x.de/r.html"}
            ]}
        """.trimIndent()

        val dto = RecipeImportParser.parse(doc, json).single()
        assertEquals(listOf("https://img.x-cdn.de/riesling-huhn.jpg"), dto.image)
    }

    /** A reference that resolves to nothing is dropped — never fetched as if it were a photo. */
    @Test
    fun danglingReferenceIsNotMistakenForAnAddress() {
        val doc = """
            {"@type":"Recipe","name":"Ohne Bild","recipeIngredient":["1 Ei"],
             "image":{"@id":"https://x.de/r.html#primaryimage"}}
        """.trimIndent()

        assertEquals(emptyList<String>(), RecipeImportParser.parse(doc, json).single().image)
    }

    /** Kochbar's shape (and everyone else's): a plain URL still passes straight through. */
    @Test
    fun plainImageUrlsAreUntouched() {
        val doc = """
            {"@type":"Recipe","name":"Tomatensalat","recipeIngredient":["4 Tomaten"],
             "image":"https://ais.kochbar.de/kbrezept/602224/tomatensalat.jpg"}
        """.trimIndent()

        assertEquals(
            listOf("https://ais.kochbar.de/kbrezept/602224/tomatensalat.jpg"),
            RecipeImportParser.parse(doc, json).single().image,
        )
    }

    /** An ImageObject that carries its own url needs no lookup. */
    @Test
    fun inlineImageObjectStillWins() {
        val doc = """
            {"@type":"Recipe","name":"Suppe","recipeIngredient":["1 Kürbis"],
             "image":{"@type":"ImageObject","@id":"#img","url":"https://x.de/suppe.jpg"}}
        """.trimIndent()

        assertEquals(listOf("https://x.de/suppe.jpg"), RecipeImportParser.parse(doc, json).single().image)
    }

    /** Springlane's shape: the scheme is inherited from the page, which only a browser does. */
    @Test
    fun protocolRelativeImageGetsAScheme() {
        val doc = """
            {"@type":"Recipe","name":"Baisertupfen","recipeIngredient":["2 Eiweiß"],
             "image":"//springlane.de/cdn/shop/files/image_6.png?v=1&width=1200"}
        """.trimIndent()

        assertEquals(
            listOf("https://springlane.de/cdn/shop/files/image_6.png?v=1&width=1200"),
            RecipeImportParser.parse(doc, json).single().image,
        )
    }

    @Test
    fun sniffAcceptsRealImagesAndRefusesAPage() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(20)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(20)
        val webp = "RIFF____WEBPVP8 ".toByteArray()
        assertTrue(ImageSniff.looksLikeImage(jpeg))
        assertTrue(ImageSniff.looksLikeImage(png))
        assertTrue(ImageSniff.looksLikeImage(webp))

        assertFalse(ImageSniff.looksLikeImage("<!DOCTYPE html><html><head><title>x".toByteArray()))
        assertFalse(ImageSniff.looksLikeImage("{\"error\":\"not found\"}".toByteArray()))
        assertFalse(ImageSniff.looksLikeImage(ByteArray(0)))
    }
}
