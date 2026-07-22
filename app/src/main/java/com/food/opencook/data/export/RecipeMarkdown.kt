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

package com.food.opencook.data.export

import com.food.opencook.data.backup.RecipeDtoEncoder
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.util.DurationFormat

/**
 * Localized labels for the Markdown export. Resolved from string resources by
 * [RecipeExporter]; kept as a plain data class so the renderer itself stays a pure
 * function that unit tests can drive without an Android Context.
 */
data class MarkdownLabels(
    val servings: String,
    val category: String,
    val prep: String,
    val cook: String,
    val total: String,
    val ingredients: String,
    /** "Ingredients (for %1$s)" — %1$s is the servings/yield text. */
    val ingredientsForTemplate: String,
    /** "%1$s servings" — turns a bare servings count into readable text. */
    val servingsValueTemplate: String,
    val instructions: String,
    val nutrition: String,
    /** First column header of the nutrition table ("Nutrient"). */
    val nutrientHeader: String,
    /** Second column header when the recipe carries no basis ("Value"). */
    val valueHeader: String,
    val calories: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val fiber: String,
    val sugar: String,
    val notes: String,
    /** "From “%1$s”" — the source cookbook in the footer. */
    val fromCookbookTemplate: String,
    /** "Exported from openCook on %1$s" — the provenance footer line. */
    val exportedTemplate: String,
)

/**
 * Renders a recipe as a standalone Markdown document. The file has to work in three
 * contexts at once: rendered (Obsidian/Typora/Joplin) it should read like a recipe card;
 * as raw text (mail, plain editor) it must still read like a recipe; and while cooking it
 * must be scannable — amounts lead each ingredient line, one step per numbered item, the
 * servings sit right in the ingredients heading.
 *
 * The photo travels as a Base64 data URI inside an HTML `<img>` tag. The bitmap is
 * pre-cropped to a slim 3:1 banner by [RecipeExporter] (center-crop, like the detail
 * screen's image header), so `width="100%"` renders it as a header strip across the
 * text column in every viewer — a portrait photo can never unroll to full height.
 * Inline HTML renders in Obsidian/Typora/Joplin/VS Code; GitHub refuses data URIs
 * either way.
 */
object RecipeMarkdown {

    fun render(
        details: RecipeWithDetails,
        labels: MarkdownLabels,
        imageDataUri: String? = null,
        exportDate: String? = null,
    ): String {
        val r = details.recipe

        // Header block, recipe-card style: cover banner on top, then title, then the
        // description as an italic lede, then the fact strip.
        val header = mutableListOf<String>()
        if (imageDataUri != null) {
            header += "<img src=\"$imageDataUri\" alt=\"${r.name.orEmpty()}\" width=\"100%\">"
        }
        header += "# ${r.name?.takeIf { it.isNotBlank() } ?: "—"}"
        r.description?.takeIf { it.isNotBlank() }?.let { header += "*${it.trim()}*" }
        metaStrip(details, labels)?.let { header += it }

        val content = listOfNotNull(
            ingredientsSection(details, labels),
            instructionsSection(details, labels),
            nutritionSection(details, labels),
            notesSection(details, labels),
            tagsLine(details),
        )

        val blocks = mutableListOf<String>()
        blocks += header
        // A rule closes the header block before the recipe content starts.
        if (content.isNotEmpty()) blocks += "---"
        blocks += content
        footer(details, labels, exportDate)?.let { blocks += it }

        return blocks.joinToString("\n\n") + "\n"
    }

    /** One compact "**Label:** value · **Label:** value" line — readable raw and rendered. */
    private fun metaStrip(details: RecipeWithDetails, labels: MarkdownLabels): String? {
        val r = details.recipe
        // Bare count here ("Portionen: 4") — the labelled form would double the word.
        val servings = r.servings?.takeIf { it > 0 }?.toString()
            ?: r.recipeYield?.takeIf { it.isNotBlank() }
        val pairs = listOfNotNull(
            r.category?.takeIf { it.isNotBlank() }?.let { labels.category to it },
            servings?.let { labels.servings to it },
            time(r.prepTime)?.let { labels.prep to it },
            time(r.cookTime)?.let { labels.cook to it },
            time(r.totalTime)?.let { labels.total to it },
        )
        if (pairs.isEmpty()) return null
        return pairs.joinToString(" · ") { (label, value) -> "**$label:** $value" }
    }

    /** "4 Portionen" from the numeric servings, else the free-text yield ("12 Stück"). */
    private fun servingsText(details: RecipeWithDetails, labels: MarkdownLabels): String? {
        val r = details.recipe
        return r.servings?.takeIf { it > 0 }?.let { labels.servingsValueTemplate.format(it.toString()) }
            ?: r.recipeYield?.takeIf { it.isNotBlank() }
    }

    private fun time(iso: String?): String? =
        iso?.let { DurationFormat.toHuman(it) }?.takeIf { it.isNotBlank() }

    private fun ingredientsSection(details: RecipeWithDetails, labels: MarkdownLabels): String? {
        val ingredients = details.ingredients.sortedBy { it.position }
        if (ingredients.isEmpty()) return null
        // Servings repeat in the heading on purpose: while scaling at the stove you
        // shouldn't have to scroll back up to the meta strip.
        val heading = servingsText(details, labels)
            ?.let { labels.ingredientsForTemplate.format(it) }
            ?: labels.ingredients
        return "## $heading\n\n" +
            ingredients.joinToString("\n") { ingredientLine(it.quantity, it.unit, it.name) }
    }

    /** "- **250 g** Mehl" — the amount in bold, cookbook-style, so it pops while cooking. */
    private fun ingredientLine(quantity: Double?, unit: String?, name: String): String {
        val amount = listOfNotNull(
            quantity?.let(RecipeDtoEncoder::formatQuantity),
            unit?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        return if (amount.isEmpty()) "- $name" else "- **$amount** $name"
    }

    private fun instructionsSection(details: RecipeWithDetails, labels: MarkdownLabels): String? {
        val steps = details.instructions.sortedBy { it.position }
        if (steps.isEmpty()) return null
        return "## ${labels.instructions}\n\n" +
            steps.mapIndexed { i, step -> "${i + 1}. ${step.text}" }.joinToString("\n")
    }

    /** Values are the printed display strings ("560 kcal") — never recomputed or invented. */
    private fun nutritionSection(details: RecipeWithDetails, labels: MarkdownLabels): String? {
        val n = details.nutrition ?: return null
        val rows = listOfNotNull(
            n.calories?.let { labels.calories to it },
            n.proteinContent?.let { labels.protein to it },
            n.fatContent?.let { labels.fat to it },
            n.carbohydrateContent?.let { labels.carbs to it },
            n.fiberContent?.let { labels.fiber to it },
            n.sugarContent?.let { labels.sugar to it },
        )
        if (rows.isEmpty()) return null
        // The basis ("pro Portion") becomes the value column's header — a real header
        // row, because an empty one renders as a bare grey strip in many viewers.
        val head = labels.nutrientHeader to (n.basis?.takeIf { it.isNotBlank() } ?: labels.valueHeader)
        // Pad the columns so the raw text lines up like a table too.
        val labelWidth = (rows + head).maxOf { it.first.length }
        val valueWidth = (rows + head).maxOf { it.second.length }
        val table = buildString {
            appendLine("| ${head.first.padEnd(labelWidth)} | ${head.second.padEnd(valueWidth)} |")
            appendLine("| ${"-".repeat(labelWidth)} | ${"-".repeat(valueWidth)} |")
            rows.forEach { (label, value) ->
                appendLine("| ${label.padEnd(labelWidth)} | ${value.padEnd(valueWidth)} |")
            }
        }.trimEnd()
        return "## ${labels.nutrition}\n\n$table"
    }

    /** Notes as a blockquote — visually an aside, not part of the recipe proper. */
    private fun notesSection(details: RecipeWithDetails, labels: MarkdownLabels): String? {
        val lines = details.recipe.notes?.split("\n")?.map(String::trim)?.filter(String::isNotEmpty)
        if (lines.isNullOrEmpty()) return null
        return "## ${labels.notes}\n\n" + lines.joinToString("\n") { "> $it" }
    }

    /** Tags as code-span chips — most viewers render them as small pills, like in the app. */
    private fun tagsLine(details: RecipeWithDetails): String? {
        val tags = details.recipe.tags?.split("\n")?.map(String::trim)?.filter(String::isNotEmpty)
        if (tags.isNullOrEmpty()) return null
        return tags.joinToString(" · ") { "`$it`" }
    }

    /** Provenance small print behind a rule, in italics so it visually recedes. */
    private fun footer(details: RecipeWithDetails, labels: MarkdownLabels, exportDate: String?): String? {
        val parts = listOfNotNull(
            details.recipe.cookbook?.takeIf { it.isNotBlank() }?.let { labels.fromCookbookTemplate.format(it) },
            exportDate?.let { labels.exportedTemplate.format(it) },
        )
        if (parts.isEmpty()) return null
        return "---\n*" + parts.joinToString(" · ") + "*"
    }
}
