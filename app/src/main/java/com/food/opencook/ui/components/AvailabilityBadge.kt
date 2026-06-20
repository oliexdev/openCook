package com.food.opencook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Compact availability pill for a planned recipe:
 * [missingCount] == 0 → ✓ "Alles da" (green); else a cart icon + the count (warm tertiary).
 * If [missingItems] is provided, tapping the pill reveals exactly which ingredients are missing.
 */
@Composable
fun AvailabilityBadge(
    missingCount: Int,
    modifier: Modifier = Modifier,
    missingItems: List<String> = emptyList(),
) {
    val ok = missingCount <= 0
    val bg = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val fg = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    var showDialog by remember { mutableStateOf(false) }
    val tappable = !ok && missingItems.isNotEmpty()
    val desc = if (ok) "Alle Zutaten vorhanden" else "$missingCount Zutaten fehlen"

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        onClick = { if (tappable) showDialog = true },
        enabled = tappable,
        modifier = modifier.semantics { contentDescription = desc },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (ok) Icons.Filled.Check else Icons.Outlined.ShoppingCart,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Text(
                if (ok) "Alles da" else missingCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("OK") } },
            title = { Text("Fehlt noch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    missingItems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            },
        )
    }
}
