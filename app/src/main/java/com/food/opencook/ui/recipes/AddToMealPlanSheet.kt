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
import com.food.opencook.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sheet that lets the user assign the current recipe to any day in the current
 * or next calendar week. Occupied days surface their currently-planned dish and
 * ask for confirmation before being overwritten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToMealPlanSheet(
    weeks: List<List<String>>,
    planned: Map<String, PlannedDish>,
    onAssign: (date: String, onDone: () -> Unit) -> Unit,
    onReplace: (date: String, onDone: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onAssigned: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replaceTarget by remember { mutableStateOf<Pair<String, PlannedDish>?>(null) }
    val today = remember { LocalDate.now().toString() }
    val dayLabelFmt = remember { DateTimeFormatter.ofPattern("EEEE dd.MM.", Locale.GERMAN) }
    val shortLabelFmt = remember { DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMAN) }

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
                        DayPickRow(
                            label = LocalDate.parse(date).format(dayLabelFmt),
                            planned = planned[date],
                            isToday = date == today,
                            onClick = {
                                val existing = planned[date]
                                if (existing == null) {
                                    onAssign(date) {
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
                    onReplace(d) {
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
        planned != null -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceContainer
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
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
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
