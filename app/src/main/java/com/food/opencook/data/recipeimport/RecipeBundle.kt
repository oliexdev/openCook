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

package com.food.opencook.data.recipeimport

import com.food.opencook.data.remote.dto.RecipeDto
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.zip.ZipInputStream

/** A parsed recipe plus its resolved/embedded image, ready for the import flow. */
data class ImportedRecipe(
    val dto: RecipeDto,
    /** Image bytes resolved locally (a `data:`-URI or a zip entry), or null. */
    val imageBytes: ByteArray? = null,
    /** An http(s) image URL to fetch best-effort (only when no local bytes), or null. */
    val imageUrl: String? = null,
)

/**
 * Reads the picked import file — either a **.zip bundle** (`recipes.json` + an `images/`
 * folder, recipes referencing images by relative path) or a **plain JSON** (one or many
 * recipes, each optionally carrying a `data:`-URI or http(s) image). Pure/offline and
 * unit-testable; the http(s) fetch is left to the caller (it needs the network).
 */
object RecipeBundle {

    fun read(bytes: ByteArray, json: Json): List<ImportedRecipe> =
        if (isZip(bytes)) readZip(bytes, json) else readJson(bytes.decodeToString(), json)

    private fun isZip(b: ByteArray): Boolean =
        b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() &&
            b[2] == 0x03.toByte() && b[3] == 0x04.toByte()

    private fun readJson(text: String, json: Json): List<ImportedRecipe> =
        RecipeImportParser.parse(text, json).map { dto ->
            when (val ref = dto.image.firstOrNull()) {
                null -> ImportedRecipe(dto)
                else -> {
                    val data = dataUriBytes(ref)
                    ImportedRecipe(
                        dto = dto,
                        imageBytes = data,
                        imageUrl = ref.takeIf { data == null && it.startsWith("http") },
                    )
                }
            }
        }

    private fun readZip(bytes: ByteArray, json: Json): List<ImportedRecipe> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries[e.name] = zis.readBytes()
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        val recipesEntry = entries.keys.firstOrNull { it.endsWith("recipes.json", ignoreCase = true) }
            ?: entries.keys.firstOrNull { it.endsWith(".json", ignoreCase = true) }
            ?: return emptyList()
        return RecipeImportParser.parse(entries.getValue(recipesEntry).decodeToString(), json).map { dto ->
            val ref = dto.image.firstOrNull()
            ImportedRecipe(
                dto = dto,
                imageBytes = ref?.let { dataUriBytes(it) ?: zipBytes(it, entries) },
                imageUrl = ref?.takeIf { it.startsWith("http") },
            )
        }
    }

    /** Decode a `data:[mime][;base64],…` URI to bytes (tolerant of whitespace), else null. */
    private fun dataUriBytes(ref: String): ByteArray? {
        if (!ref.startsWith("data:")) return null
        val comma = ref.indexOf(',').takeIf { it >= 0 } ?: return null
        val payload = ref.substring(comma + 1)
        return runCatching { Base64.getMimeDecoder().decode(payload) }.getOrNull()
    }

    /** Resolve a relative image path against the zip entries (tolerant of leading ./ or a subdir). */
    private fun zipBytes(ref: String, entries: Map<String, ByteArray>): ByteArray? {
        if (ref.startsWith("data:") || ref.startsWith("http")) return null
        val norm = ref.removePrefix("./").removePrefix("/")
        entries[norm]?.let { return it }
        val base = norm.substringAfterLast('/')
        return entries.entries.firstOrNull { it.key.substringAfterLast('/') == base }?.value
    }
}
