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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.food.opencook.R
import com.food.opencook.data.backup.RecipeDtoEncoder
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.remote.dto.RecipeDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

/** The single-recipe export formats offered by the detail screen's share sheet. */
enum class ExportFormat(val mime: String, val extension: String) {
    /** Human-readable Markdown with the photo embedded as a data URI. */
    MARKDOWN("text/markdown", "md"),

    /** schema.org/Recipe JSON-LD — the interchange format other tools understand. */
    SCHEMA_ORG_JSON("application/json", "json"),
}

/**
 * Renders one recipe into a document the user just created via the SAF file picker —
 * the single-recipe sibling of [com.food.opencook.data.backup.LocalBackupManager].
 * Markdown rendering itself lives in [RecipeMarkdown] (pure, unit-tested); this class
 * contributes the Android bits: localized labels, image downscaling, and the write.
 */
@Singleton
class RecipeExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {
    // Same tolerant settings as everywhere else, but indented so an exported file
    // opens like a document, not like a wire payload.
    private val prettyJson = Json(from = json) { prettyPrint = true }

    fun fileName(details: RecipeWithDetails, format: ExportFormat): String =
        exportFileName(details.recipe.name, format.extension)

    /** Render and write into [target]; a failed write deletes the half-written document. */
    suspend fun export(details: RecipeWithDetails, target: Uri, format: ExportFormat) =
        withContext(Dispatchers.IO) {
            val text = when (format) {
                ExportFormat.MARKDOWN -> renderMarkdown(details)
                ExportFormat.SCHEMA_ORG_JSON -> renderJson(details)
            }
            try {
                val out = context.contentResolver.openOutputStream(target)
                    ?: error("Cannot open $target")
                out.use { it.write(text.toByteArray()) }
            } catch (e: Exception) {
                // Never leave a truncated file behind — same rule as LocalBackupManager.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
                throw e
            }
        }

    /** A standalone JSON file carries no image reference — there is no `images/` beside it. */
    private fun renderJson(details: RecipeWithDetails): String =
        prettyJson.encodeToString(RecipeDto.serializer(), RecipeDtoEncoder.encode(details, imageRef = null)) + "\n"

    private fun renderMarkdown(details: RecipeWithDetails): String =
        RecipeMarkdown.render(
            details = details,
            labels = labels(),
            imageDataUri = imageDataUri(details),
            exportDate = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
        )

    private fun labels() = MarkdownLabels(
        servings = context.getString(R.string.recipe_export_md_servings),
        category = context.getString(R.string.recipe_export_md_category),
        prep = context.getString(R.string.recipe_export_md_prep),
        cook = context.getString(R.string.recipe_export_md_cook),
        total = context.getString(R.string.recipe_export_md_total),
        ingredients = context.getString(R.string.review_ingredients),
        ingredientsForTemplate = context.getString(R.string.recipe_export_md_ingredients_for),
        servingsValueTemplate = context.getString(R.string.wizard_summary_servings),
        instructions = context.getString(R.string.review_instructions),
        nutrition = context.getString(R.string.review_nutrition),
        nutrientHeader = context.getString(R.string.recipe_export_md_nutrient),
        valueHeader = context.getString(R.string.recipe_export_md_value),
        calories = context.getString(R.string.recipe_export_md_calories),
        protein = context.getString(R.string.nutrition_protein),
        fat = context.getString(R.string.nutrition_fat),
        carbs = context.getString(R.string.nutrition_carbs),
        fiber = context.getString(R.string.recipe_export_md_fiber),
        sugar = context.getString(R.string.recipe_export_md_sugar),
        notes = context.getString(R.string.review_notes),
        fromCookbookTemplate = context.getString(R.string.recipe_export_md_from_cookbook),
        exportedTemplate = context.getString(R.string.recipe_export_md_footer),
    )

    /**
     * The primary photo as a `data:image/jpeg;base64,…` URI, center-cropped to the
     * [BANNER_RATIO] strip the detail screen's header uses and downscaled to at most
     * [MAX_IMAGE_DIM] px wide, so the Markdown file stays a few hundred KB and the
     * photo renders as a banner in every viewer — a portrait shot must never unroll
     * to full height in the document. Null when the recipe has no local image bytes
     * (e.g. synced in but never downloaded).
     */
    private fun imageDataUri(details: RecipeWithDetails): String? {
        val file = primaryImageFile(details) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // Power-of-two sampling close to the target, then crop + exact scale.
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_DIM) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null

            // Center-crop to the banner strip (same framing as ContentScale.Crop in the app).
            val cropWidth: Int
            val cropHeight: Int
            if (decoded.width.toFloat() / decoded.height >= BANNER_RATIO) {
                cropHeight = decoded.height
                cropWidth = (decoded.height * BANNER_RATIO).toInt().coerceAtMost(decoded.width)
            } else {
                cropWidth = decoded.width
                cropHeight = (decoded.width / BANNER_RATIO).toInt().coerceAtMost(decoded.height)
            }
            val cropped = Bitmap.createBitmap(
                decoded,
                (decoded.width - cropWidth) / 2,
                (decoded.height - cropHeight) / 2,
                cropWidth,
                cropHeight,
            ).also { if (it !== decoded) decoded.recycle() }

            val bitmap = if (cropped.width > MAX_IMAGE_DIM) {
                Bitmap.createScaledBitmap(
                    cropped,
                    MAX_IMAGE_DIM,
                    (MAX_IMAGE_DIM / BANNER_RATIO).toInt().coerceAtLeast(1),
                    true,
                ).also { if (it !== cropped) cropped.recycle() }
            } else {
                cropped
            }
            val bytes = ByteArrayOutputStream().use { buffer ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer)
                bitmap.recycle()
                buffer.toByteArray()
            }
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    /** Mirrors [com.food.opencook.data.backup.BackupWriter.primaryImageFile]. */
    private fun primaryImageFile(details: RecipeWithDetails): File? =
        details.images
            .sortedByDescending { it.isPrimary }
            .firstNotNullOfOrNull { img ->
                img.localPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
            }

    companion object {
        private const val MAX_IMAGE_DIM = 1024
        private const val JPEG_QUALITY = 80

        /** Banner width:height — a slim strip that heads the document without dominating it. */
        private const val BANNER_RATIO = 3.0f

        /** "Omas Pfannkuchen" → "Omas-Pfannkuchen.md"; strips filesystem-hostile characters. */
        fun exportFileName(name: String?, extension: String): String {
            val slug = name.orEmpty()
                .replace(Regex("""[\\/:*?"<>|]"""), "")
                .trim()
                .replace(Regex("""\s+"""), "-")
            return (slug.ifEmpty { "recipe" }) + "." + extension
        }
    }
}
