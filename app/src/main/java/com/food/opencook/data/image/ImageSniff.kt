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

package com.food.opencook.data.image

/**
 * Is this actually a picture? A web import follows an address that a foreign page handed us, and
 * a wrong one does not fail loudly — a recipe page answers 200 with a few hundred KB of HTML,
 * which would then be stored and synced as the dish photo. Checked by signature rather than by
 * `Content-Type`, because the header is the part a server is most likely to get wrong.
 */
object ImageSniff {

    fun looksLikeImage(bytes: ByteArray): Boolean = when {
        bytes.size < 12 -> false
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> true                              // JPEG
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> true // PNG
        bytes.ascii(0, "GIF8") -> true                                          // GIF
        bytes.ascii(0, "RIFF") && bytes.ascii(8, "WEBP") -> true                // WebP
        bytes.ascii(4, "ftyp") -> true                                          // HEIC/AVIF
        bytes.ascii(0, "BM") -> true                                            // BMP
        else -> false
    }

    private fun ByteArray.startsWith(vararg signature: Int): Boolean =
        size >= signature.size && signature.withIndex().all { (i, b) -> this[i] == b.toByte() }

    private fun ByteArray.ascii(offset: Int, text: String): Boolean =
        size >= offset + text.length &&
            text.indices.all { this[offset + it] == text[it].code.toByte() }
}
