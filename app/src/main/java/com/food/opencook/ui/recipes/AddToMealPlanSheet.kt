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

package com.food.opencook.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.food.opencook.R
import com.food.opencook.ui.mealplan.MealPlanSlots
import com.food.opencook.ui.theme.Spacing
import java.time.LocalDate
import com.food.opencook.util.DateLabels

/**
 * Sheet that lets the user assign the current recipe to any of the next fourteen days,
 * grouped by calendar week (which is what [weeks] carries — one group per week, in order,
 * starting with the one today falls in). Occupied days surface their currently-planned dish
 * and ask for confirmation before being overwritten.
 *
 * With more than one meal planned, a chip row picks the meal *once* for the whole sheet
 * rather than per day — you are adding one dish, and "which meal" is a property of the
 * dish, not of each of the fourteen days. It starts on the meal the recipe is marked for,
 * so a cake opens on "Snack" without anyone having to think about it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToMealPlanSheet(
    weeks: List<List<String>>,
    planned: Map<String, PlannedDish>,
    plannedMeals: List<String>,
    recipeMealTypes: List<String>,
    onAssign: (date: String, slot: String, onDone: () -> Unit) -> Unit,
    onReplace: (date: String, slot: String, onDone: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onAssigned: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replaceTarget by remember { mutableStateOf<Pair<String, PlannedDish>?>(null) }
    val today = remember { LocalDate.now().toString() }
    val dayLabelFmt = remember { DateLabels.weekdayDayMonth(fullWeekday = true) }
    val shortLabelFmt = remember { DateLabels.weekdayDayMonth() }
    // Default to the recipe's own meal if the household plans it, else the first planned one.
    var slot by remember(plannedMeals, recipeMealTypes) {
        mutableStateOf(plannedMeals.firstOrNull { it in recipeMealTypes } ?: plannedMeals.first())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                stringResource(R.string.recipe_plan_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (plannedMeals.size > 1) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    plannedMeals.forEach { key ->
                        FilterChip(
                            selected = key == slot,
                            onClick = { slot = key },
                            label = { Text(stringResource(MealPlanSlots.shortLabelRes(key))) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                weeks.forEachIndexed { weekIndex, dates ->
                    item(key = "h_$weekIndex") {
                        // The first group is whatever week today sits in, so the offset is the
                        // group's index — starting mid-week, a rolling fortnight can reach into
                        // a third one.
                        Text(
                            text = stringResource(
                                when (weekIndex) {
                                    0 -> R.string.mealplan_week_current
                                    1 -> R.string.mealplan_week_next
                                    else -> R.string.mealplan_week_after_next
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    items(dates, key = { it }) { date ->
                        // What's in *this meal* on that day — a planned breakfast must not
                        // make the dinner row look occupied.
                        val existing = planned[RecipeDetailViewModel.cellKey(date, slot)]
                        DayPickRow(
                            label = LocalDate.parse(date).format(dayLabelFmt),
                            planned = existing,
                            isToday = date == today,
                            onClick = {
                                if (existing == null) {
                                    onAssign(date, slot) {
                                        onAssigned(LocalDate.parse(date).format(shortLabelFmt))
                                        onDismiss()
                                    }
                                } else {
                                    replaceTarget = date to existing
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    replaceTarget?.let { (date, existing) ->
        AlertDialog(
            onDismissRequest = { replaceTarget = null },
            title = { Text(stringResource(R.string.recipe_plan_replace_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recipe_plan_replace_text,
                        LocalDate.parse(date).format(dayLabelFmt),
                        existing.name,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = {
                    val d = date
                    replaceTarget = null
                    onReplace(d, slot) {
                        onAssigned(LocalDate.parse(d).format(shortLabelFmt))
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.recipe_plan_replace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { replaceTarget = null }) {
                    Text(stringResource(R.string.processing_cancel))
                }
            },
        )
    }
}

@Composable
private fun DayPickRow(
    label: String,
    planned: PlannedDish?,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val container = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        planned != null -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                planned?.name ?: stringResource(R.string.recipe_plan_day_free),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Small thumbnail of the dish currently planned for this day — empty days show nothing.
        if (planned != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (planned.imageModel != null) {
                    AsyncImage(
                        model = planned.imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
