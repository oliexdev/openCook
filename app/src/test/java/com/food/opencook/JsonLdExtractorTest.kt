package com.food.opencook

import com.food.opencook.data.recipeimport.JsonLdExtractor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JsonLdExtractorTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private fun extract(html: String) = JsonLdExtractor.extractFirstRecipe(html, json)

    @Test
    fun findsRecipeAmongMultipleBlocksAndGraph() {
        // Mirrors a real page: a BreadcrumbList block, then a @graph holding the Recipe.
        val html = """
            <html><head>
            <script type="application/ld+json">
              {"@context":"https://schema.org","@type":"BreadcrumbList","itemListElement":[]}
            </script>
            <script type="application/ld+json">
              {"@context":"https://schema.org","@graph":[
                {"@type":"WebPage","name":"x"},
                {"@type":"Recipe","name":"One-Pot-Pasta",
                 "image":[{"@type":"ImageObject","url":"https://img.example/rezept.jpg"}],
                 "recipeIngredient":["300 g Pasta","150 g Chorizo"],
                 "recipeInstructions":"Alles in einen Topf. Garen."}
              ]}
            </script>
            </head><body></body></html>
        """.trimIndent()

        val imp = extract(html)
        assertNotNull(imp)
        assertEquals("One-Pot-Pasta", imp!!.dto.name)
        assertEquals(2, imp.dto.openCookIngredients.size)
        // image is an http(s) URL → exposed for the caller to fetch.
        assertEquals("https://img.example/rezept.jpg", imp.imageUrl)
    }

    @Test
    fun retriesWithHtmlEntitiesDecoded() {
        // Some CMSes HTML-encode the whole JSON-LD block, so even the structural quotes are
        // entities — raw parse fails, the decoded retry rescues it.
        val html = """
            <script type="application/ld+json">
              {&quot;@type&quot;:&quot;Recipe&quot;,&quot;name&quot;:&quot;Käsekuchen&quot;,&quot;recipeIngredient&quot;:[&quot;Quark&quot;]}
            </script>
        """.trimIndent()
        assertEquals("Käsekuchen", extract(html)!!.dto.name)
    }

    @Test
    fun returnsNullWhenNoRecipe() {
        val html = """
            <script type="application/ld+json">
              {"@type":"NewsArticle","headline":"Kein Rezept hier"}
            </script>
        """.trimIndent()
        assertNull(extract(html))
    }

    @Test
    fun returnsNullWhenNoJsonLdAtAll() {
        assertNull(extract("<html><body><h1>Nur Text</h1></body></html>"))
    }
}
