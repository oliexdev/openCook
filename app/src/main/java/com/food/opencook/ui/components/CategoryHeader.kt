package com.food.opencook.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.food.opencook.ui.theme.Spacing
import com.food.opencook.util.GroceryCategory

/**
 * A grocery-aisle section header (emoji + name) shared by the shopping list and the
 * pantry, so both group their items by the same [GroceryCategory] and look like siblings.
 */
@Composable
fun CategoryHeader(category: GroceryCategory, modifier: Modifier = Modifier) {
    Text(
        "${category.emoji}  ${category.displayName}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}
