package com.food.opencook.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

/**
 * App-wide snackbar host, provided by [MainScaffold] so any screen — and the shared
 * top bar — can surface a message without threading the state through every call.
 * The default standalone instance keeps previews/tests from crashing (messages just
 * have no host to render them).
 */
val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }
