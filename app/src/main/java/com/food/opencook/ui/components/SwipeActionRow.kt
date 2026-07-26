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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.food.opencook.R
import com.food.opencook.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A confirmed swipe, reported up to the list so the overlay can animate outside the (removed) row. */
data class SwipeConfirmEvent(
    val boundsInRoot: Rect,
    val deleting: Boolean,
    val tick: Int,
)

/**
 * Wraps a list row with the app-wide swipe convention (pantry + shopping): **swipe left =
 * delete**, **swipe right = the screen's positive action** (pantry → shopping list; shopping →
 * pantry). Swipe is only an accelerator — every action is also in the row's ⋮ menu.
 *
 * The confirmation glow is **not** drawn here (the row often disappears immediately). Instead a
 * confirmed swipe reports its on-screen bounds via [onConfirm]; the host list draws the rising
 * pill with [SwipeConfirmLayer], so it survives the row's removal.
 *
 * Rows that leave the list slide fully off (`confirmValueChange` returns true where the row is
 * removed; the caller's `animateItem()` collapses the gap). The pantry's "→ shopping" keeps the
 * item in stock, so it snaps back (`actionRemovesRow = false`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionRow(
    onDelete: () -> Unit,
    onAction: () -> Unit,
    actionIcon: ImageVector,
    actionLabel: String,
    onConfirm: (deleting: Boolean, boundsInRoot: Rect) -> Unit,
    modifier: Modifier = Modifier,
    actionRemovesRow: Boolean = true,
    peek: Boolean = false,
    onPeekShown: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var rowBounds by remember { mutableStateOf(Rect.Zero) }

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); onConfirm(true, rowBounds); true }
                SwipeToDismissBoxValue.StartToEnd -> { onAction(); onConfirm(false, rowBounds); actionRemovesRow }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    // One-time peek: gently reveal both swipe actions in turn (right = positive, left = delete),
    // then settle back — a discoverability hint. Purely visual, independent of [state].
    val peekOffset = remember { Animatable(0f) }
    val revealPx = with(LocalDensity.current) { 80.dp.toPx() }
    LaunchedEffect(peek) {
        if (peek) {
            delay(450)
            peekOffset.animateTo(revealPx, tween(360))
            delay(500)
            peekOffset.animateTo(0f, tween(280))
            peekOffset.animateTo(-revealPx, tween(360))
            delay(500)
            peekOffset.animateTo(0f, tween(280))
            onPeekShown()
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier.onGloballyPositioned { rowBounds = it.boundsInRoot() },
        backgroundContent = {
            val direction = when {
                peekOffset.value > 0.5f -> SwipeToDismissBoxValue.StartToEnd
                peekOffset.value < -0.5f -> SwipeToDismissBoxValue.EndToStart
                else -> state.dismissDirection
            }
            SwipeBackground(direction = direction, actionIcon = actionIcon, actionLabel = actionLabel)
        },
        content = {
            Box(Modifier.graphicsLayer { translationX = peekOffset.value }) { content() }
        },
    )
}

/** The subtle themed reveal behind the row while swiping (and during the peek). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    direction: SwipeToDismissBoxValue,
    actionIcon: ImageVector,
    actionLabel: String,
) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val deleting = direction == SwipeToDismissBoxValue.EndToStart
    val color = if (deleting) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val onColor = if (deleting) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val icon = if (deleting) Icons.Outlined.Delete else actionIcon
    val label = if (deleting) stringResource(R.string.shopping_delete) else actionLabel

    // Icon on the outer revealed edge (delete reveals from the right, action from the left) so
    // the icon is the first thing seen as the row moves.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (deleting) Arrangement.End else Arrangement.Start,
    ) {
        val labelText = @Composable {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = onColor,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        val iconGlyph = @Composable {
            Icon(icon, contentDescription = null, tint = onColor, modifier = Modifier.size(22.dp))
        }
        if (deleting) { labelText(); iconGlyph() } else { iconGlyph(); labelText() }
    }
}

/**
 * List-level overlay that draws the confirmation glow for the most recent [event], positioned at
 * the row's on-screen bounds — so it plays even though the row itself is being removed. Host it
 * inside the same Box that fills the list screen, passing that Box's root offset as
 * [boxOffsetInRoot]. A glowing pill rises from below the row on the matching side (delete → right,
 * add → left) and fades out: red "− Entfernen" / green "+ <destination>".
 */
@Composable
fun SwipeConfirmLayer(
    event: SwipeConfirmEvent?,
    boxOffsetInRoot: androidx.compose.ui.geometry.Offset,
    addLabel: String,
    modifier: Modifier = Modifier,
) {
    if (event == null) return
    val deleteLabel = stringResource(R.string.shopping_delete)
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(22f) }
    LaunchedEffect(event.tick) {
        if (event.tick == 0) return@LaunchedEffect
        alpha.snapTo(0f)
        rise.snapTo(22f)
        launch { rise.animateTo(0f, tween(240)) } // rise up from below the row
        alpha.animateTo(1f, tween(160))
        delay(340)
        launch { rise.animateTo(-8f, tween(280)) }
        alpha.animateTo(0f, tween(280)) // then fade out
    }
    if (alpha.value <= 0.01f) return

    val deleting = event.deleting
    val accent = if (deleting) SwipeRed else SwipeGreen
    val density = LocalDensity.current
    val topPx = (event.boundsInRoot.top - boxOffsetInRoot.y).roundToInt()

    Box(
        modifier
            .offset { IntOffset(0, topPx) }
            .fillMaxWidth()
            .height(with(density) { event.boundsInRoot.height.toDp() }),
    ) {
        // Translucent "frosted" pill: the whole shape fades with [alpha], the fill is
        // additionally see-through, but the icon/label are drawn opaque so the text stays crisp.
        Row(
            Modifier
                .align(if (deleting) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .offset { IntOffset(0, rise.value.dp.roundToPx()) }
                .alpha(alpha.value)
                .shadow(8.dp, CircleShape, ambientColor = accent, spotColor = accent) // soft glow
                .background(accent.copy(alpha = 0.55f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (deleting) Icons.Rounded.Remove else Icons.Rounded.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (deleting) deleteLabel else addLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

// Semantic overlay colors (legible on both light and dark rows).
private val SwipeRed = Color(0xFFE53935)
private val SwipeGreen = Color(0xFF2E9E4F)
