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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.food.opencook.data.local.entity.MealSlot
import com.food.opencook.ui.theme.Spacing
import java.time.LocalDate
import com.food.opencook.util.DateLabels

/**
 * Sheet that lets the user assign the current recipe to any day in the current
 * or next calendar week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToMealPlanSheet(
    weeks: List<List<String>>,
    planned: Map<String, List<PlannedDish>>, // Changed to List for slots
    onAssign: (date: String, slot: MealSlot, onDone: () -> Unit) -> Unit,
    onReplace: (date: String, slot: MealSlot, onDone: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onAssigned: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replaceTarget by remember { mutableStateOf<Triple<String, MealSlot, PlannedDish>?>(null) }
    val today = remember { LocalDate.now().toString() }
    val dayLabelFmt = remember { DateLabels.weekdayDayMonth(fullWeekday = true) }
    val shortLabelFmt = remember { DateLabels.weekdayDayMonth() }

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
            Spacer(Modifier.height(Spacing.md))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                weeks.forEachIndexed { weekIndex, dates ->
                    item(key = "h_$weekIndex") {
                        Text(
                            text = stringResource(
                                if (weekIndex == 0) R.string.mealplan_week_current
                                else R.string.mealplan_week_next,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    items(dates, key = { it }) { date ->
                        val dayPlanned = planned[date].orEmpty()
                        DayPickSection(
                            dateLabel = LocalDate.parse(date).format(dayLabelFmt),
                            planned = dayPlanned,
                            isToday = date == today,
                            onPick = { slot ->
                                val existing = dayPlanned.find { it.slot == slot }
                                if (existing == null) {
                                    onAssign(date, slot) {
                                        onAssigned(LocalDate.parse(date).format(shortLabelFmt))
                                        onDismiss()
                                    }
                                } else {
                                    replaceTarget = Triple(date, slot, existing)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    replaceTarget?.let { (date, slot, existing) ->
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
                    val s = slot
                    replaceTarget = null
                    onReplace(d, s) {
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
private fun DayPickSection(
    dateLabel: String,
    planned: List<PlannedDish>,
    isToday: Boolean,
    onPick: (MealSlot) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(dateLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.xs))
        MealSlot.entries.forEach { slot ->
            val dish = planned.find { it.slot == slot }
            val slotLabel = when(slot) {
                MealSlot.BREAKFAST -> stringResource(R.string.mealplan_slot_breakfast)
                MealSlot.LUNCH -> stringResource(R.string.mealplan_slot_lunch)
                MealSlot.DINNER -> stringResource(R.string.mealplan_slot_dinner)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(slot) }
                    .padding(horizontal = Spacing.xs, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    slotLabel.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        dish?.name ?: stringResource(R.string.recipe_plan_day_free),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dish != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dish != null) {
                    AsyncImage(
                        model = dish.imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}
