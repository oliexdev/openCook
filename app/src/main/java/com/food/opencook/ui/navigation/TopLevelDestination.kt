package com.food.opencook.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations for the adaptive navigation (bottom bar on phones,
 * rail/drawer on tablets). Scanning is contextual (add-recipe / barcode), not a tab.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Heute", Icons.Outlined.Home),
    RECIPES("recipes", "Rezepte", Icons.AutoMirrored.Outlined.MenuBook),
    PLAN("plan", "Plan", Icons.Outlined.CalendarMonth),
    SHOPPING("shopping", "Einkauf", Icons.Outlined.ShoppingCart),
    SETTINGS("settings", "Mehr", Icons.Outlined.Tune),
}
