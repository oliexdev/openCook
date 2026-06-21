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

import com.food.opencook.data.recipeimport.RecipeImportParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeImportParserTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private fun parse(s: String) = RecipeImportParser.parse(s, json)

    @Test
    fun schemaOrgSingleObject() {
        val r = parse(
            """{"@type":"Recipe","name":"Pfannkuchen",
               "recipeIngredient":["200 g Mehl","3 Eier"],
               "recipeInstructions":[{"@type":"HowToStep","text":"Verrühren."},{"text":"Backen."}],
               "recipeYield":"4 Portionen"}""",
        )
        assertEquals(1, r.size)
        assertEquals("Pfannkuchen", r[0].name)
        val ings = r[0].openCookIngredients
        assertEquals(2, ings.size)
        assertEquals(200.0, ings[0].quantity!!, 1e-9); assertEquals("g", ings[0].unit); assertEquals("Mehl", ings[0].name)
        assertEquals(3.0, ings[1].quantity!!, 1e-9); assertNull(ings[1].unit); assertEquals("Eier", ings[1].name)
        assertEquals(listOf("Verrühren.", "Backen."), r[0].recipeInstructions.map { it.text })
        assertEquals(4, r[0].openCookServings)
    }

    @Test
    fun cookbookFromIsPartOf() {
        // schema.org standard: the source cookbook is expressed via isPartOf → Book.name.
        val obj = parse(
            """{"@type":"Recipe","name":"Curry","recipeIngredient":["Reis"],
               "isPartOf":{"@type":"Book","name":"365 Low Carb"}}""",
        )
        assertEquals("365 Low Carb", obj[0].cookbook)
        // A bare-string isPartOf is also accepted.
        val str = parse("""{"name":"Suppe","recipeIngredient":["Wasser"],"isPartOf":"Mein Buch"}""")
        assertEquals("Mein Buch", str[0].cookbook)
        // Absent → null.
        val none = parse("""{"name":"Brot","recipeIngredient":["Mehl"]}""")
        assertNull(none[0].cookbook)
    }

    @Test
    fun arrayOfPlainShape() {
        // Plain capitalised shape with a single instruction string and an ignored Url.
        val r = parse(
            """[{"Name":"Gulasch","Ingredients":["500 g Rind","2 Zwiebeln"],
                "Instructions":"Anbraten.\nSchmoren.","Url":"https://example.com/x"},
               {"Name":"Salat","Ingredients":["Gurke"],"Instructions":"Schneiden."}]""",
        )
        assertEquals(2, r.size)
        assertEquals("Gulasch", r[0].name)
        assertEquals(listOf("Anbraten.", "Schmoren."), r[0].recipeInstructions.map { it.text })
        assertEquals(500.0, r[0].openCookIngredients[0].quantity!!, 1e-9)
        assertEquals("Rind", r[0].openCookIngredients[0].name)
        // The source Url must never leak into the imported recipe.
        assertTrue(r.none { it.openCookIngredients.any { i -> i.name.contains("example.com") } })
    }

    @Test
    fun jsonLdGraphKeepsOnlyRecipes() {
        val r = parse(
            """{"@context":"https://schema.org","@graph":[
                 {"@type":"WebPage","name":"Seite"},
                 {"@type":"Organization","name":"Verlag"},
                 {"@type":"Recipe","name":"Suppe","recipeIngredient":["Wasser"],
                  "recipeInstructions":["Kochen."]}]}""",
        )
        assertEquals(1, r.size)
        assertEquals("Suppe", r[0].name)
    }

    @Test
    fun instructionsAsStringListAndIngredientObjects() {
        val r = parse(
            """{"name":"Test","recipeIngredient":[{"text":"1 Apfel"},{"name":"Zucker"}],
               "recipeInstructions":["Schritt eins","Schritt zwei"]}""",
        )
        val ings = r[0].openCookIngredients
        assertEquals(1.0, ings[0].quantity!!, 1e-9); assertEquals("Apfel", ings[0].name)
        assertNull(ings[1].quantity); assertEquals("Zucker", ings[1].name)
        assertEquals(2, r[0].recipeInstructions.size)
    }

    @Test
    fun howToSectionIsFlattened() {
        val r = parse(
            """{"name":"X","recipeIngredient":["a"],"recipeInstructions":[
                 {"@type":"HowToSection","itemListElement":[
                    {"@type":"HowToStep","text":"S1"},{"@type":"HowToStep","text":"S2"}]}]}""",
        )
        assertEquals(listOf("S1", "S2"), r[0].recipeInstructions.map { it.text })
    }

    @Test
    fun nutritionParsedWhenPresent() {
        val r = parse(
            """{"name":"N","recipeIngredient":["a"],"recipeInstructions":["b"],
               "nutrition":{"@type":"NutritionInformation","calories":"560 kcal","proteinContent":"17 g"}}""",
        )
        assertEquals("560 kcal", r[0].nutrition?.calories)
        assertEquals("17 g", r[0].nutrition?.proteinContent)
    }

    @Test
    fun invalidEntriesAreSkipped() {
        // No name, or no ingredients+instructions → dropped; valid one survives.
        val r = parse(
            """[{"recipeIngredient":["x"]},
                {"name":"Leer"},
                {"name":"Gut","recipeIngredient":["x"],"recipeInstructions":["y"]}]""",
        )
        assertEquals(1, r.size)
        assertEquals("Gut", r[0].name)
    }

    @Test
    fun singleBlobInstructionsSplitIntoSentences() {
        val r = parse(
            """{"name":"B","recipeIngredient":["1 EL Öl"],
               "recipeInstructions":"Eier kochen. Dann pellen.Den Reis kochen."}""",
        )
        assertEquals(
            listOf("Eier kochen.", "Dann pellen.", "Den Reis kochen."),
            r[0].recipeInstructions.map { it.text },
        )
        assertEquals("Öl", r[0].openCookIngredients[0].name)
        assertEquals("EL", r[0].openCookIngredients[0].unit)
    }

    @Test
    fun emptyInputYieldsNothing() {
        assertEquals(0, parse("[]").size)
        assertEquals(0, parse("""{"foo":"bar"}""").size)
    }

    @Test
    fun nutritionDroppedWhenEmpty() {
        val r = parse(
            """{"name":"N","recipeIngredient":["a"],"recipeInstructions":["b"],"nutrition":{}}""",
        )
        assertNull(r[0].nutrition)
    }

    @Test
    fun nutritionUnitsAndBasisAreKept() {
        // schema.org NutritionInformation: values carry their unit; servingSize → basis.
        val n = parse(
            """{"name":"N","recipeIngredient":["a"],"recipeInstructions":["b"],
               "nutrition":{"@type":"NutritionInformation","calories":"320 kcal",
                 "proteinContent":"20 g","fatContent":"12 g","carbohydrateContent":"30 g",
                 "servingSize":"pro Portion"}}""",
        )[0].nutrition!!
        assertEquals("320 kcal", n.calories)
        assertEquals("20 g", n.proteinContent)
        assertEquals("12 g", n.fatContent)
        assertEquals("30 g", n.carbohydrateContent)
        assertEquals("pro Portion", n.openCookBasis)
    }

    @Test
    fun imageReferencesAreKept() {
        // String, array and ImageObject forms; verbatim (data-URI / relative path / http URL).
        assertEquals(
            listOf("images/dish.jpg"),
            parse("""{"name":"A","recipeIngredient":["a"],"image":"images/dish.jpg"}""")[0].image,
        )
        assertEquals(
            listOf("a.jpg", "b.jpg"),
            parse("""{"name":"A","recipeIngredient":["a"],"image":["a.jpg","b.jpg"]}""")[0].image,
        )
        assertEquals(
            listOf("https://h/i.jpg"),
            parse("""{"name":"A","recipeIngredient":["a"],
                     "image":{"@type":"ImageObject","url":"https://h/i.jpg"}}""")[0].image,
        )
    }
}
