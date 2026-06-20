package com.food.opencook.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** One falling, fluttering, spinning confetti piece — all geometry is fractional/relative so
 *  it scales to whatever canvas size it's drawn into. */
private data class ConfettiPiece(
    val xFraction: Float,
    val color: Color,
    val sizeDp: Float,
    val startRotation: Float,
    val rotationTurns: Float,
    val driftFraction: Float,
    val fallDelay: Float,
    val fallSpeed: Float,
    val flutterFreq: Float,
    val flutterAmpDp: Float,
    val circle: Boolean,
)

/**
 * A brief, full-bleed confetti burst. Draws nothing while [visible] is false; when it flips
 * true it animates ~2.2s of confetti raining down and then calls [onFinished] so the caller
 * can reset its trigger. The canvas has no pointer-input modifiers, so it never intercepts
 * taps — the UI underneath stays fully interactive during the celebration.
 */
@Composable
fun ConfettiOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.error,
    )

    // Re-randomised each time the burst starts (key = visible), so two completions in a row
    // don't look identical.
    val pieces = remember(visible) {
        val rnd = Random(System.nanoTime())
        List(120) {
            ConfettiPiece(
                xFraction = rnd.nextFloat(),
                color = palette[rnd.nextInt(palette.size)],
                sizeDp = 6f + rnd.nextFloat() * 8f,
                startRotation = rnd.nextFloat() * 360f,
                rotationTurns = (rnd.nextFloat() - 0.5f) * 5f,
                driftFraction = (rnd.nextFloat() - 0.5f) * 0.3f,
                fallDelay = rnd.nextFloat() * 0.25f,
                fallSpeed = 0.85f + rnd.nextFloat() * 0.5f,
                flutterFreq = 1f + rnd.nextFloat() * 2f,
                flutterAmpDp = 6f + rnd.nextFloat() * 16f,
                circle = rnd.nextInt(4) == 0,
            )
        }
    }

    val progress = remember(visible) { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 2200, easing = LinearEasing))
        onFinished()
    }

    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        pieces.forEach { p ->
            // Per-piece local time so pieces start staggered and finish at different moments.
            val local = ((t - p.fallDelay) / (1f - p.fallDelay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach

            val margin = 48.dp.toPx()
            val y = -margin + local * (size.height + margin * 2f) * p.fallSpeed
            val flutter = sin(local * p.flutterFreq * 2f * PI.toFloat()) * p.flutterAmpDp.dp.toPx()
            val x = p.xFraction * size.width + p.driftFraction * size.width * local + flutter

            // Fade out over the last 20% of the piece's life.
            val alpha = if (local < 0.8f) 1f else (1f - (local - 0.8f) / 0.2f).coerceIn(0f, 1f)
            val color = p.color.copy(alpha = alpha)
            val side = p.sizeDp.dp.toPx()

            if (p.circle) {
                drawCircle(color = color, radius = side / 2f, center = Offset(x, y))
            } else {
                val w = side
                val h = side * 0.45f
                rotate(degrees = p.startRotation + p.rotationTurns * 360f * local, pivot = Offset(x, y)) {
                    drawRect(color = color, topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h))
                }
            }
        }
    }
}
