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

package com.food.opencook.ui.barcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Decodes product barcodes (EAN-13/8, UPC-A) from CameraX frames with ZXing — no
 * ML Kit (proprietary). Reads only the Y (luminance) plane, which is all ZXing needs.
 *
 * Frames arrive in sensor orientation, so the luminance is rotated to upright before
 * decoding — otherwise a 1D barcode held normally has its bars perpendicular to ZXing's
 * row scan and never decodes. If that fails the same frame is retried rotated an
 * extra 90° (covering an etiquette held sideways) — only when the upright pass yields
 * nothing, so the common case still costs one decode.
 *
 * To filter out ZXing's occasional false positives at frame transitions (especially
 * just after [rearm] when the user's hand is still moving and the camera hasn't
 * stabilised), [onBarcode] is fired only after the SAME code has been decoded in
 * two consecutive frames. That removes the symptom where the first re-scan attempt
 * captures a garbage EAN that the product lookup can't resolve.
 *
 * Fires once per stable code; [rearm] re-enables scanning and resets the stability state.
 */
class BarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                ),
                // Spend more effort per frame — live camera frames are noisier than a still
                // photo and the user expects a hands-free scan.
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    @Volatile private var handled = false
    @Volatile private var lastCandidate: String? = null
    @Volatile private var candidateHits: Int = 0

    /** Re-enable scanning after a result was consumed (e.g. on rescan). Clears the
     *  stability buffer too so a freshly-armed scanner doesn't immediately commit on
     *  whatever stale candidate was in flight. */
    fun rearm() {
        handled = false
        lastCandidate = null
        candidateHits = 0
    }

    override fun analyze(image: ImageProxy) {
        if (handled) { image.close(); return }
        try {
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val w = image.width
            val h = image.height
            // Pack the Y plane into a contiguous w*h array, dropping any row padding.
            val packed = ByteArray(w * h)
            val buffer = plane.buffer
            for (y in 0 until h) {
                buffer.position(y * rowStride)
                buffer.get(packed, y * w, w)
            }
            // First pass: rotate to upright (matching what the user sees) so 1D bars
            // cross ZXing's row scan.
            val (uprightLum, uw, uh) = rotate(packed, w, h, image.imageInfo.rotationDegrees)
            var text = tryDecode(uprightLum, uw, uh)
            if (text.isNullOrBlank()) {
                // Fallback: rotate the upright frame an extra 90° and try once more —
                // catches barcodes whose label happens to be perpendicular to the user's
                // grip. Only the (rare) frames that didn't decode upright pay this cost.
                val (rotated, rw, rh) = rotate(uprightLum, uw, uh, 90)
                text = tryDecode(rotated, rw, rh)
            }
            if (!text.isNullOrBlank()) {
                // Stability gate: a single decode is a false-positive risk (motion blur,
                // partial frame, holographic glints on the package). Require two
                // consecutive frames to agree on the same code before committing.
                if (text == lastCandidate) {
                    candidateHits += 1
                    if (candidateHits >= STABILITY_HITS) {
                        handled = true
                        onBarcode(text)
                    }
                } else {
                    lastCandidate = text
                    candidateHits = 1
                }
            }
        } catch (_: Exception) {
            // No barcode in this frame (or a transient decode error) — keep scanning.
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun tryDecode(lum: ByteArray, w: Int, h: Int): String? {
        val source = PlanarYUVLuminanceSource(lum, w, h, 0, 0, w, h, false)
        return runCatching { reader.decode(BinaryBitmap(HybridBinarizer(source)))?.text }.getOrNull()
            .also { reader.reset() }
    }

    /** Rotate a packed [w]x[h] luminance buffer by [degrees] (0/90/180/270, clockwise). */
    private fun rotate(src: ByteArray, w: Int, h: Int, degrees: Int): Triple<ByteArray, Int, Int> {
        if (degrees != 90 && degrees != 180 && degrees != 270) return Triple(src, w, h)
        val out = ByteArray(w * h)
        var i = 0
        when (degrees) {
            90 -> for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = src[i++]
            180 -> for (y in 0 until h) for (x in 0 until w) out[(h - 1 - y) * w + (w - 1 - x)] = src[i++]
            270 -> for (y in 0 until h) for (x in 0 until w) out[(w - 1 - x) * h + y] = src[i++]
        }
        return if (degrees == 180) Triple(out, w, h) else Triple(out, h, w)
    }

    private companion object {
        /** Frames-in-a-row a candidate must show up before it counts as the captured scan. */
        const val STABILITY_HITS = 2
    }
}
