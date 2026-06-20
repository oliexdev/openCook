package com.food.opencook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the display awake while this composable is in the composition, and releases the
 * hold automatically when it leaves. Used on the shopping list so the screen doesn't lock
 * mid-shop while the phone sits on the cart.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
