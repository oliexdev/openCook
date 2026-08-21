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

package com.food.opencook.ui.retrospect

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.food.opencook.R
import com.food.opencook.ui.components.EmptyState
import com.food.opencook.ui.theme.Spacing
import java.time.Month
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

/** Thumbnail edge, matched to the meal plan's dish rows so a recipe row weighs the same
 *  wherever you meet it. */
private val ROW_THUMB = 52.dp

/** Same hairline weight as the plan's week headers. */
private const val DIVIDER_ALPHA = 0.4f

/**
 * "What we've cooked" — the household's cooking history, which the app has always recorded
 * and never shown: `meal_plan` is never pruned, but the plan list only reaches a week back.
 *
 * One continuous list, newest first: every confirmed meal under the month it happened in,
 * back to the very first one. Deliberately no charts, no streaks, no badges — a household
 * that cooks four times a week does not need to be congratulated for it. The month headers
 * stick, so it stays clear which month is being read while scrolling years of it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RetrospectScreen(
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    viewModel: RetrospectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.retrospect_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.retrospect_empty_title),
                message = stringResource(R.string.retrospect_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding),
            // On a tablet the name and its date would otherwise drift a hand's width apart.
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            state.months.forEach { month ->
                stickyHeader(key = "month_${month.key}") { MonthHeader(month) }
                itemsIndexed(month.rows, key = { index, _ -> "${month.key}_$index" }) { _, row ->
                    MealRow(row, onClick = { onOpenRecipe(row.recipeId) })
                }
            }
        }
    }
}

/**
 * The month and how much was cooked in it. Opaque on purpose: it floats over the rows
 * scrolling beneath it, and a translucent header would let a dish photo bleed through.
 */
@Composable
private fun MonthHeader(month: RetrospectMonth) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.xs),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(month.label, style = MaterialTheme.typography.titleSmall)
            Text(
                pluralStringResource(R.plurals.retrospect_month_meals, month.count, month.count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = DIVIDER_ALPHA))
    }
}

/** One meal: the dish and the day it happened. The whole row opens the recipe. */
@Composable
private fun MealRow(row: RetrospectRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (row.imageModel != null) {
            AsyncImage(
                model = row.imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ROW_THUMB).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            // Same placeholder as the plan rows — a bare colour block reads as a broken image
            // once several stack up, the glyph reads as "recipe without a photo".
            Box(
                Modifier.size(ROW_THUMB).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.dayLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Month name in the device's language. Read through [LocalConfiguration] rather than
 * `Locale.getDefault()` so a language change recomposes the label instead of leaving the
 * previous language on screen. Used by the plan list's entry card.
 */
@Composable
internal fun monthName(month: Month): String =
    month.getDisplayName(TextStyle.FULL_STANDALONE, LocalConfiguration.current.locales[0])
