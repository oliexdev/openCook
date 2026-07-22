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

import com.food.opencook.data.backup.RecipeDtoEncoder
import com.food.opencook.data.export.MarkdownLabels
import com.food.opencook.data.export.RecipeExporter
import com.food.opencook.data.export.RecipeMarkdown
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.recipeimport.RecipeImportParser
import com.food.opencook.data.remote.dto.RecipeDto
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * The single-recipe export: the Markdown document must carry every printed section (and
 * only those), and the JSON file must stay parseable by our own import path so a shared
 * file round-trips into another openCook.
 */
class RecipeExportTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private var previousLocale: Locale = Locale.getDefault()

    @Before
    fun pinLocale() {
        // DurationFormat renders "Std/Min" vs "h/min" from the default locale.
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMAN)
    }

    @After
    fun restoreLocale() = Locale.setDefault(previousLocale)

    private val labels = MarkdownLabels(
        servings = "Portionen",
        category = "Kategorie",
        prep = "Vorbereitung",
        cook = "Kochzeit",
        total = "Gesamt",
        ingredients = "Zutaten",
        ingredientsForTemplate = "Zutaten (für %1\$s)",
        servingsValueTemplate = "%1\$s Portionen",
        instructions = "Zubereitung",
        nutrition = "Nährwerte",
        nutrientHeader = "Nährwert",
        valueHeader = "Wert",
        calories = "Kalorien",
        protein = "Eiweiß",
        fat = "Fett",
        carbs = "Kohlenhydrate",
        fiber = "Ballaststoffe",
        sugar = "Zucker",
        notes = "Notizen",
        fromCookbookTemplate = "Aus „%1\$s“",
        exportedTemplate = "Exportiert aus openCook am %1\$s",
    )

    private fun sample() = RecipeWithDetails(
        recipe = RecipeEntity(
            id = "recipe-1",
            name = "Pfannkuchen",
            description = "Dünn und golden.",
            recipeYield = "4 Portionen",
            servings = 4,
            category = "Dessert",
            notes = "Teig 30 Min ruhen lassen\nMit Zucker servieren",
            tags = "süß\nschnell",
            lastCookedAt = "2026-07-01",
            cookbook = "Omas Küche",
            prepTime = "PT10M",
            cookTime = "PT1H20M",
            totalTime = "PT90M",
            sourcePhotoId = null,
            householdId = null,
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_500_000,
        ),
        ingredients = listOf(
            IngredientEntity("ing-1", "recipe-1", 0, 250.0, "g", "Mehl"),
            IngredientEntity("ing-2", "recipe-1", 1, 3.0, null, "Eier"),
            IngredientEntity("ing-3", "recipe-1", 2, null, null, "Salz"),
        ),
        instructions = listOf(
            InstructionEntity("step-1", "recipe-1", 0, "Alles verrühren."),
            InstructionEntity("step-2", "recipe-1", 1, "In der Pfanne backen."),
        ),
        images = emptyList(),
        nutrition = NutritionEntity("recipe-1", "560 kcal", "17 g", "22 g", "70 g", null, "3 g", "pro Portion"),
    )

    // --- Markdown ---

    @Test
    fun markdownCarriesEverySection() {
        val md = RecipeMarkdown.render(sample(), labels, imageDataUri = null, exportDate = "22.07.2026")
        assertTrue(md.startsWith("# Pfannkuchen\n"))
        // Description is the italic lede between title and meta strip.
        assertTrue(md.contains("*Dünn und golden.*"))
        assertTrue(md.contains("**Kategorie:** Dessert"))
        assertTrue(md.contains("**Portionen:** 4 ·"))
        assertTrue(md.contains("**Vorbereitung:** 10 Min"))
        assertTrue(md.contains("**Kochzeit:** 1 Std 20 Min"))
        assertTrue(md.contains("**Gesamt:** 1 Std 30 Min"))
        // A rule closes the header block before the content.
        assertTrue(md.contains("---\n\n## Zutaten (für 4 Portionen)"))
        // Amounts are bold, cookbook-style; a bare ingredient stays plain.
        assertTrue(md.contains("- **250 g** Mehl"))
        assertTrue(md.contains("- **3** Eier"))
        assertTrue(md.contains("- Salz"))
        assertTrue(md.contains("## Zubereitung"))
        assertTrue(md.contains("1. Alles verrühren."))
        assertTrue(md.contains("2. In der Pfanne backen."))
        // The basis is the value column's header, not a heading suffix.
        assertTrue(md.contains("## Nährwerte"))
        assertFalse(md.contains("## Nährwerte (pro Portion)"))
        assertTrue(md.contains("| Nährwert"))
        assertTrue(md.contains("| pro Portion"))
        assertTrue(md.contains("| Kalorien"))
        assertTrue(md.contains("| 560 kcal"))
        assertTrue(md.contains("## Notizen"))
        assertTrue(md.contains("> Teig 30 Min ruhen lassen"))
        assertTrue(md.contains("> Mit Zucker servieren"))
        // Tags as code-span chips, footer as italic small print.
        assertTrue(md.contains("`süß` · `schnell`"))
        assertTrue(md.contains("---\n*Aus „Omas Küche“ · Exportiert aus openCook am 22.07.2026*"))
    }

    @Test
    fun emptySectionsAreOmittedEntirely() {
        val bare = sample().copy(
            recipe = sample().recipe.copy(
                description = null, notes = null, tags = null, cookbook = null,
                category = null, prepTime = null, cookTime = null, totalTime = null,
                servings = null, recipeYield = null,
            ),
            nutrition = null,
        )
        val md = RecipeMarkdown.render(bare, labels, imageDataUri = null, exportDate = null)
        assertFalse(md.contains("## Nährwerte"))
        assertFalse(md.contains("## Notizen"))
        assertFalse(md.contains("`"))          // no tag chips
        assertFalse(md.contains("Kategorie"))  // no meta strip
        assertFalse(md.contains("*Aus"))       // no footer
        assertTrue(md.contains("## Zutaten\n"))
        // Exactly one rule: the header/content divider — no footer rule.
        assertEquals(1, md.lines().count { it == "---" })
    }

    @Test
    fun photoIsAFullWidthCoverBanner() {
        val uri = "data:image/jpeg;base64,AAAA"
        val md = RecipeMarkdown.render(sample(), labels, imageDataUri = uri, exportDate = null)
        // Cover style: the banner opens the document, the title sits beneath it. The
        // exporter pre-crops the bitmap to a slim 3:1 strip, so 100% width renders as
        // a header band — a plain Markdown image would show a portrait photo full-height.
        assertTrue(md.startsWith("<img src=\"$uri\" alt=\"Pfannkuchen\" width=\"100%\">\n\n# Pfannkuchen\n"))

        val withoutImage = RecipeMarkdown.render(sample(), labels, imageDataUri = null, exportDate = null)
        assertFalse(withoutImage.contains("<img"))
    }

    @Test
    fun zeroDurationsAndBlankFieldsDoNotLeak() {
        val odd = sample().copy(
            recipe = sample().recipe.copy(prepTime = "PT0M", cookTime = null, totalTime = "PT900S"),
        )
        val md = RecipeMarkdown.render(odd, labels, imageDataUri = null, exportDate = null)
        assertFalse(md.contains("Vorbereitung"))   // zero-length duration renders nothing
        assertFalse(md.contains("PT0M"))
        assertTrue(md.contains("**Gesamt:** 15 Min")) // seconds fold into minutes
    }

    // --- JSON (the exporter serializes exactly this encoder output, pretty-printed) ---

    @Test
    fun jsonExportRoundTripsThroughOurOwnImportParser() {
        val pretty = Json(from = json) { prettyPrint = true }
        val text = pretty.encodeToString(RecipeDto.serializer(), RecipeDtoEncoder.encode(sample(), imageRef = null))
        // What foreign tools look for is in the file itself: the JSON-LD markers and
        // the flattened ingredient lines.
        assertTrue(text.contains("\"@context\": \"https://schema.org\""))
        assertTrue(text.contains("\"@type\": \"Recipe\""))
        assertTrue(text.contains("250 g Mehl"))
        // Our own import parser rebuilds DTOs (markers dropped, structured ingredients
        // preferred over the flattened lines) and still gets the recipe back intact.
        val parsed = RecipeImportParser.parse(text, json)
        assertEquals(1, parsed.size)
        assertEquals("recipe-1", parsed[0].identifier)
        assertEquals("Pfannkuchen", parsed[0].name)
        assertEquals(listOf("Mehl", "Eier", "Salz"), parsed[0].openCookIngredients.map { it.name })
        assertEquals(listOf(250.0, 3.0, null), parsed[0].openCookIngredients.map { it.quantity })
        assertTrue(parsed[0].image.isEmpty())
    }

    // --- File names ---

    @Test
    fun fileNamesAreSluggedAndNeverEmpty() {
        assertEquals("Omas-Pfannkuchen.md", RecipeExporter.exportFileName("Omas Pfannkuchen", "md"))
        assertEquals("Fisch-Chips.json", RecipeExporter.exportFileName("Fisch / \"Chips\"?", "json"))
        assertEquals("recipe.md", RecipeExporter.exportFileName(null, "md"))
        assertEquals("recipe.md", RecipeExporter.exportFileName("  ", "md"))
    }
}
