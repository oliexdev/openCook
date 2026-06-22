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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Simple, dependency-free image edits (rotate 90°, crop) on local image files.
 *
 * Edits are pixel-baked and re-encoded into a fresh cache file (the source is never
 * mutated): each edit returns a new path the caller attaches as a new local-only
 * [ImageEntity], so it re-uploads under a new content-addressed name and propagates
 * via the normal sync path. EXIF orientation is normalised on decode so the saved
 * pixels match what Coil shows (Coil also auto-applies EXIF), keeping crop fractions
 * aligned with the on-screen image.
 */
@Singleton
class ImageEditor @Inject constructor(
    private val imageStore: ImageStore,
) {
    /** Rotate 90° clockwise; returns the new file path. */
    suspend fun rotate90(srcPath: String): String = withContext(Dispatchers.IO) {
        val src = decodeUpright(srcPath)
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f) }, true)
        writeJpeg(rotated)
    }

    /**
     * Crop to the rectangle given as fractions (0..1) of the upright image. Coordinates
     * are clamped to the image and to a minimum 1px so a degenerate selection can't crash.
     */
    suspend fun crop(srcPath: String, left: Float, top: Float, right: Float, bottom: Float): String =
        withContext(Dispatchers.IO) {
            val src = decodeUpright(srcPath)
            val x = (left * src.width).roundToInt().coerceIn(0, src.width - 1)
            val y = (top * src.height).roundToInt().coerceIn(0, src.height - 1)
            val w = ((right - left) * src.width).roundToInt().coerceIn(1, src.width - x)
            val h = ((bottom - top) * src.height).roundToInt().coerceIn(1, src.height - y)
            writeJpeg(Bitmap.createBitmap(src, x, y, w, h))
        }

    private fun writeJpeg(bitmap: Bitmap): String {
        val dest = imageStore.newCaptureFile()
        dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return dest.absolutePath
    }

    /** Decode a file and bake in its EXIF orientation so the bitmap is visually upright. */
    private fun decodeUpright(path: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(path)
            ?: throw IllegalArgumentException("Cannot decode image: $path")
        val matrix = when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
            ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
