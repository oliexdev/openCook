package com.food.opencook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.food.opencook.R

/**
 * Compact "Gekocht" pill, mirroring [AvailabilityBadge]'s shape so cooked status reads at a
 * glance on planned-meal rows and the home dashboard. The same restaurant glyph as the toggle.
 * Uses the theme's herb-green secondary so it tracks light/dark + dynamic color.
 */
@Composable
fun CookedBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.Restaurant, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.recipe_cooked_badge), style = MaterialTheme.typography.labelMedium)
        }
    }
}
