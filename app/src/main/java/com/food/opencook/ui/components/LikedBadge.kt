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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.food.opencook.R

/**
 * "Somebody in the household likes this" — a red heart on an opaque disc, sitting on a
 * recipe photo.
 *
 * Opaque rather than translucent on purpose: it has to stay legible over a bright photo as
 * well as a dark one, and the theme background gives it the same weight in both schemes.
 * Shared by the detail screen's image header and the recipe cards in the list, so the same
 * mark means the same thing wherever a dish shows up.
 */
@Composable
fun LikedBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.padding(10.dp),
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.recipe_liked_label),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(7.dp).size(18.dp),
        )
    }
}
