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

package com.food.opencook.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Deterministic pseudo-random in [0,1) from an int seed (stable per frame). */
private fun rnd(seed: Int): Float {
    val x = sin(seed * 127.1f) * 43758.5453f
    return x - floor(x)
}

/**
 * Screen-filling "like" burst: hearts spray outward from a point and arc away with a little
 * gravity, fading out. Keyed on [tick]; draw over the whole screen (`Modifier.fillMaxSize()`).
 */
@Composable
fun HeartBurstFull(tick: Int, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1400, easing = LinearOutSlowInEasing))
    }
    val p = progress.value
    if (p <= 0f || p >= 1f) return
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height * 0.55f)
        val diag = hypot(size.width, size.height)
        val ease = 1f - (1f - p) * (1f - p) * (1f - p) // ease-out cubic
        val alpha = (1f - p).coerceIn(0f, 1f)
        val n = 16
        for (i in 0 until n) {
            val angle = ((i / n.toFloat()) * 2f * PI + (rnd(i) - 0.5f) * 0.7f).toFloat()
            val speed = (0.35f + 0.55f * rnd(i + 7)) * diag * 0.55f
            val dx = cos(angle) * speed * ease
            val dy = sin(angle) * speed * ease - 0.10f * diag * ease // slight upward bias
            val gravity = 0.22f * diag * p * p
            val pos = Offset(center.x + dx, center.y + dy + gravity)
            val hs = diag * 0.03f * (0.7f + rnd(i + 3) * 0.9f)
            drawPath(heartPath(pos, hs), color.copy(alpha = alpha))
        }
    }
}

/**
 * Screen-filling "cooked" reward: the cooked (fork & knife) icon rains down in assorted sizes,
 * each with its own spin and a sideways sway, tinted in the cooked badge colour ([color]) with a
 * soft transparent-black outline so it still reads against a photo. Keyed on [tick].
 */
@Composable
fun CookedCelebration(tick: Int, color: Color, outlineColor: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(2600, easing = LinearEasing)) // constant time → even falling
    }
    val painter = rememberVectorPainter(Icons.Outlined.Restaurant)
    val p = progress.value
    if (p <= 0f || p >= 1f) return
    val fill = ColorFilter.tint(color)
    val outline = ColorFilter.tint(outlineColor)
    Canvas(modifier) {
        val n = 16
        for (i in 0 until n) {
            val start = rnd(i + 5) * 0.45f // when this icon enters
            if (p < start) continue
            // Own fall duration (speed) as a varied fraction of the time it has left, so icons
            // are scattered across heights AND all finish (exit the bottom) before the end —
            // none freeze mid-air.
            val dur = (1f - start) * (0.55f + rnd(i + 2) * 0.45f)
            val fall = ((p - start) / dur).coerceIn(0f, 1f)
            val s = size.minDimension * (0.035f + rnd(i + 8) * 0.085f) // assorted (smaller) sizes
            // Sway sideways while falling (like a leaf) so they don't drop in straight lines.
            val sway = sin(fall * PI.toFloat() * 2.2f + rnd(i + 13) * 6.28f) * size.width * 0.03f
            // Inset the spawn column so the icon's half-width never spills past the left/right edge.
            val margin = s / 2f + size.width * 0.04f
            val x = margin + rnd(i) * (size.width - 2f * margin) + sway
            val y = -s + fall * (size.height + 2f * s)
            // Fade with vertical position so the half-clipped moments at the top/bottom screen
            // edges are transparent — you only see whole icons in the body of the screen.
            val yc = y + s / 2f
            val edge = size.height * 0.12f
            val a = (yc / edge).coerceIn(0f, 1f) * ((size.height - yc) / edge).coerceIn(0f, 1f)
            // Per-icon spin: own start angle, own speed, and either direction.
            val spinDir = if (rnd(i + 11) < 0.5f) -1f else 1f
            val deg = rnd(i + 9) * 360f + fall * (140f + rnd(i + 15) * 200f) * spinDir
            rotate(deg, pivot = Offset(x, y)) {
                // Subtle, matching outline: same icon stamped in a ring of 8 small offsets.
                val o = s * 0.05f
                for (k in 0 until 8) {
                    val ang = (PI.toFloat() / 4f) * k
                    translate(left = x - s / 2f + cos(ang) * o, top = y - s / 2f + sin(ang) * o) {
                        with(painter) { draw(Size(s, s), alpha = a * 0.4f, colorFilter = outline) }
                    }
                }
                translate(left = x - s / 2f, top = y - s / 2f) {
                    with(painter) { draw(Size(s, s), alpha = a * 0.9f, colorFilter = fill) }
                }
            }
        }
    }
}

private fun heartPath(center: Offset, s: Float): Path = Path().apply {
    val cx = center.x
    val cy = center.y
    moveTo(cx, cy + s * 0.35f)
    cubicTo(cx - s * 0.75f, cy - s * 0.10f, cx - s * 0.55f, cy - s * 0.78f, cx, cy - s * 0.30f)
    cubicTo(cx + s * 0.55f, cy - s * 0.78f, cx + s * 0.75f, cy - s * 0.10f, cx, cy + s * 0.35f)
    close()
}
