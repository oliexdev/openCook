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

package com.food.opencook.ui.review

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.food.opencook.R
import com.food.opencook.util.RecipeCategories
import com.food.opencook.ui.theme.Spacing
import kotlin.math.roundToInt

/* ------------------------------------------------------------------------- */
/* Step 1 — Bild & Basics                                                     */
/* ------------------------------------------------------------------------- */

@Composable
fun BasicsStep(
    recipe: EditableRecipe,
    viewModel: ReviewViewModel,
    index: Int,
    onTakePhoto: () -> Unit,
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.attachImage(index, uri) }

    StepScroll {
        ImageHero(
            recipe = recipe,
            viewModel = viewModel,
            index = index,
            onTakePhoto = onTakePhoto,
            onPickGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        Spacer(Modifier.height(Spacing.lg))

        WizardTextField(
            label = stringResource(R.string.review_name),
            value = recipe.name,
            onValueChange = { v -> viewModel.updateRecipe(index) { it.copy(name = v) } },
        )
        Spacer(Modifier.height(Spacing.md))

        ServingsStepper(
            servings = recipe.servings,
            onChange = { v -> viewModel.updateRecipe(index) { it.copy(servings = v) } },
        )
        Spacer(Modifier.height(Spacing.md))

        CategoryChips(
            current = recipe.category,
            onPick = { v -> viewModel.updateRecipe(index) { it.copy(category = v) } },
        )
        Spacer(Modifier.height(Spacing.md))

        WizardTextField(
            label = stringResource(R.string.review_cookbook),
            value = recipe.cookbook,
            onValueChange = { v -> viewModel.updateRecipe(index) { it.copy(cookbook = v) } },
        )
    }
}

@Composable
private fun ImageHero(
    recipe: EditableRecipe,
    viewModel: ReviewViewModel,
    index: Int,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
) {
    val primary = recipe.images.firstOrNull { it.isPrimary } ?: recipe.images.firstOrNull()
    val imageUrl = primary?.let { viewModel.imageUrlFor(it) }
    val canEdit = imageUrl != null && viewModel.canEditImage(index)
    var cropping by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp).align(Alignment.Center),
            )
        }
        if (canEdit) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FilledTonalIconButton(onClick = { viewModel.rotateImage(index) }) {
                    Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = stringResource(R.string.review_image_rotate))
                }
                FilledTonalIconButton(onClick = { cropping = true }) {
                    Icon(Icons.Outlined.Crop, contentDescription = stringResource(R.string.review_image_crop))
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledTonalButton(onClick = onTakePhoto) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.review_image_take))
            }
            FilledTonalButton(onClick = onPickGallery) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.review_image_gallery))
            }
        }
    }
    if (cropping && imageUrl != null) {
        CropDialog(
            model = imageUrl,
            onCancel = { cropping = false },
            onApply = { l, t, r, b -> viewModel.cropImage(index, l, t, r, b); cropping = false },
        )
    }
}

/**
 * Full-screen crop overlay: a draggable/resizable rectangle over the image. The image is
 * sized to its own aspect ratio with [ContentScale.Fit], so the crop box coordinates map
 * 1:1 to image fractions (0..1) — which is what [ReviewViewModel.cropImage] expects.
 */
@Composable
private fun CropDialog(
    model: Any,
    onCancel: () -> Unit,
    onApply: (Float, Float, Float, Float) -> Unit,
) {
    val painter = rememberAsyncImagePainter(model)
    val intrinsic = painter.intrinsicSize
    var rect by remember { mutableStateOf(floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f)) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(intrinsic.width / intrinsic.height)) {
                        Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        val density = LocalDensity.current
                        val boxW = constraints.maxWidth.toFloat()
                        val boxH = constraints.maxHeight.toFloat()
                        val minPx = with(density) { 48.dp.toPx() }
                        val handleDp = 28.dp
                        val handlePx = with(density) { handleDp.toPx() }
                        var l by remember(boxW, boxH) { mutableStateOf(boxW * 0.1f) }
                        var t by remember(boxW, boxH) { mutableStateOf(boxH * 0.1f) }
                        var r by remember(boxW, boxH) { mutableStateOf(boxW * 0.9f) }
                        var b by remember(boxW, boxH) { mutableStateOf(boxH * 0.9f) }
                        fun emit() { rect = floatArrayOf(l / boxW, t / boxH, r / boxW, b / boxH) }

                        Canvas(Modifier.fillMaxSize()) {
                            val scrim = Color.Black.copy(alpha = 0.5f)
                            drawRect(scrim, Offset(0f, 0f), Size(boxW, t))
                            drawRect(scrim, Offset(0f, b), Size(boxW, boxH - b))
                            drawRect(scrim, Offset(0f, t), Size(l, b - t))
                            drawRect(scrim, Offset(r, t), Size(boxW - r, b - t))
                            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 2.dp.toPx()))
                        }
                        // Move the whole selection.
                        Box(
                            Modifier
                                .offset { IntOffset(l.roundToInt(), t.roundToInt()) }
                                .size(with(density) { (r - l).toDp() }, with(density) { (b - t).toDp() })
                                .pointerInput(boxW, boxH) {
                                    detectDragGestures { change, d ->
                                        change.consume()
                                        val w = r - l; val h = b - t
                                        val nl = (l + d.x).coerceIn(0f, boxW - w)
                                        val nt = (t + d.y).coerceIn(0f, boxH - h)
                                        l = nl; r = nl + w; t = nt; b = nt + h; emit()
                                    }
                                },
                        )
                        // Four corner handles.
                        CropHandle(l, t) { dx, dy -> l = (l + dx).coerceIn(0f, r - minPx); t = (t + dy).coerceIn(0f, b - minPx); emit() }
                        CropHandle(r - handlePx, t) { dx, dy -> r = (r + dx).coerceIn(l + minPx, boxW); t = (t + dy).coerceIn(0f, b - minPx); emit() }
                        CropHandle(l, b - handlePx) { dx, dy -> l = (l + dx).coerceIn(0f, r - minPx); b = (b + dy).coerceIn(t + minPx, boxH); emit() }
                        CropHandle(r - handlePx, b - handlePx) { dx, dy -> r = (r + dx).coerceIn(l + minPx, boxW); b = (b + dy).coerceIn(t + minPx, boxH); emit() }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.crop_cancel))
                }
                FilledTonalButton(
                    onClick = { onApply(rect[0], rect[1], rect[2], rect[3]) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.crop_apply))
                }
            }
        }
    }
}

/** A draggable 28dp corner handle positioned at the given pixel offset within the crop box. */
@Composable
private fun CropHandle(xPx: Float, yPx: Float, onDrag: (Float, Float) -> Unit) {
    Box(
        Modifier
            .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectDragGestures { change, d -> change.consume(); onDrag(d.x, d.y) }
            },
    )
}

@Composable
private fun ServingsStepper(servings: String, onChange: (String) -> Unit) {
    val value = servings.toIntOrNull() ?: 0
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(stringResource(R.string.review_servings), modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { if (value > 1) onChange((value - 1).toString()) }) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            if (value > 0) value.toString() else "—",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onChange(((value.takeIf { it > 0 } ?: 0) + 1).toString()) }) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Step 2 — Zutaten                                                          */
/* ------------------------------------------------------------------------- */

@Composable
fun IngredientsStep(
    recipe: EditableRecipe,
    viewModel: ReviewViewModel,
    index: Int,
) {
    StepScroll {
        if (recipe.ingredients.isEmpty()) {
            EmptyHint(stringResource(R.string.wizard_no_ingredients))
        } else {
            recipe.ingredients.forEachIndexed { i, ingredient ->
                IngredientCard(
                    ingredient = ingredient,
                    onChange = { transform ->
                        viewModel.updateRecipe(index) {
                            it.copy(ingredients = it.ingredients.mapIndexed { j, ing -> if (j == i) transform(ing) else ing })
                        }
                    },
                    onApplySuggestion = { viewModel.applySuggestion(index, i) },
                    onRemove = { viewModel.removeIngredient(index, i) },
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        FilledTonalButton(
            onClick = { viewModel.addIngredient(index) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.review_add_ingredient))
        }
    }
}

@Composable
private fun IngredientCard(
    ingredient: EditableIngredient,
    onChange: ((EditableIngredient) -> EditableIngredient) -> Unit,
    onApplySuggestion: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            // Name is the primary field — give it the full width.
            OutlinedTextField(
                value = ingredient.name,
                onValueChange = { v ->
                    onChange { it.copy(name = v, suggestion = null, autoCorrected = false) }
                },
                label = { Text(stringResource(R.string.wizard_ingredient_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = ingredient.quantity,
                    onValueChange = { v -> onChange { it.copy(quantity = v) } },
                    label = { Text(stringResource(R.string.wizard_ingredient_qty)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ingredient.unit,
                    onValueChange = { v -> onChange { it.copy(unit = v) } },
                    label = { Text(stringResource(R.string.wizard_ingredient_unit)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.review_remove))
                }
            }
            when {
                ingredient.suggestion != null -> TextButton(onClick = onApplySuggestion) {
                    Text(
                        stringResource(R.string.wizard_correction_suggest, ingredient.suggestion),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                ingredient.autoCorrected -> Text(
                    stringResource(R.string.wizard_correction_auto),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Step 3 — Schritte                                                          */
/* ------------------------------------------------------------------------- */

@Composable
fun StepsStep(
    recipe: EditableRecipe,
    viewModel: ReviewViewModel,
    index: Int,
) {
    StepScroll {
        if (recipe.instructions.isEmpty()) {
            EmptyHint(stringResource(R.string.wizard_no_steps))
        } else {
            recipe.instructions.forEachIndexed { i, step ->
                StepCard(
                    number = i + 1,
                    text = step.text,
                    isFirst = i == 0,
                    isLast = i == recipe.instructions.lastIndex,
                    onChange = { v ->
                        viewModel.updateRecipe(index) {
                            it.copy(instructions = it.instructions.mapIndexed { j, s -> if (j == i) s.copy(text = v) else s })
                        }
                    },
                    onMoveUp = { viewModel.moveStep(index, i, i - 1) },
                    onMoveDown = { viewModel.moveStep(index, i, i + 1) },
                    onRemove = { viewModel.removeStep(index, i) },
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        FilledTonalButton(
            onClick = { viewModel.addStep(index) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.review_add_step))
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    text: String,
    isFirst: Boolean,
    isLast: Boolean,
    onChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onMoveUp, enabled = !isFirst) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.wizard_step_move_up))
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.wizard_step_move_down))
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.review_remove))
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Step 4 — Optionales / Nährwerte                                            */
/* ------------------------------------------------------------------------- */

private val TIME_PRESETS = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)

@Composable
fun DetailsStep(
    recipe: EditableRecipe,
    viewModel: ReviewViewModel,
    index: Int,
) {
    StepScroll {
        TimeStepper(
            label = stringResource(R.string.review_prep_time),
            humanValue = recipe.prepTime,
            onChange = { v -> viewModel.updateRecipe(index) { it.copy(prepTime = v) } },
        )
        Spacer(Modifier.height(Spacing.lg))
        TimeStepper(
            label = stringResource(R.string.review_cook_time),
            humanValue = recipe.cookTime,
            onChange = { v -> viewModel.updateRecipe(index) { it.copy(cookTime = v) } },
        )
        Spacer(Modifier.height(Spacing.lg))

        Text(stringResource(R.string.review_notes), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = recipe.notes,
            onValueChange = { v -> viewModel.updateRecipe(index) { it.copy(notes = v) } },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            minLines = 3,
        )

        Spacer(Modifier.height(Spacing.lg))
        NutritionSection(recipe = recipe, viewModel = viewModel, index = index)
    }
}

/**
 * Minutes-as-chips: tap one to set the time, then nudge with ±5 / ±15 buttons.
 * Storage uses the existing humanized format ("30 Min", "1 Std 10 Min") so
 * [com.food.opencook.util.DurationFormat.toIso] keeps working on save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeStepper(
    label: String,
    humanValue: String,
    onChange: (String) -> Unit,
) {
    val currentMinutes = parseHumanMinutes(humanValue)
    Text(label, style = MaterialTheme.typography.titleSmall)
    Text(
        humanMinutes(currentMinutes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Spacing.xs),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TIME_PRESETS.forEach { mins ->
            FilterChip(
                selected = currentMinutes == mins,
                onClick = { onChange(formatHumanMinutes(mins)) },
                label = { Text(humanMinutes(mins)) },
            )
        }
    }
    Row(
        Modifier.padding(top = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        OutlinedButton(onClick = { onChange(formatHumanMinutes((currentMinutes - 5).coerceAtLeast(0))) }) {
            Text("−5")
        }
        OutlinedButton(onClick = { onChange(formatHumanMinutes((currentMinutes - 15).coerceAtLeast(0))) }) {
            Text("−15")
        }
        OutlinedButton(onClick = { onChange(formatHumanMinutes(currentMinutes + 5)) }) { Text("+5") }
        OutlinedButton(onClick = { onChange(formatHumanMinutes(currentMinutes + 15)) }) { Text("+15") }
    }
}

private fun parseHumanMinutes(text: String): Int {
    val iso = com.food.opencook.util.DurationFormat.toIso(text) ?: return 0
    return com.food.opencook.util.DurationFormat.minutes(iso) ?: 0
}

@Composable
private fun humanMinutes(total: Int): String {
    if (total <= 0) return stringResource(R.string.wizard_time_none)
    val hours = total / 60
    val mins = total % 60
    return when {
        hours > 0 && mins > 0 -> stringResource(R.string.wizard_time_hour_minutes, hours, mins)
        hours > 0 -> stringResource(R.string.wizard_time_hour, hours)
        else -> stringResource(R.string.wizard_time_minutes, mins)
    }
}

private fun formatHumanMinutes(total: Int): String {
    if (total <= 0) return ""
    val hours = total / 60
    val mins = total % 60
    return when {
        hours > 0 && mins > 0 -> "$hours Std $mins Min"
        hours > 0 -> "$hours Std"
        else -> "$mins Min"
    }
}

@Composable
private fun NutritionSection(
    recipe: EditableRecipe,
    viewModel: ReviewViewModel,
    index: Int,
) {
    var expanded by remember { mutableStateOf(recipe.nutrition != null) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.wizard_nutrition_toggle),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = expanded,
            onCheckedChange = { on ->
                expanded = on
                if (on && recipe.nutrition == null) {
                    viewModel.updateRecipe(index) {
                        it.copy(nutrition = EditableNutrition("", "", "", "", ""))
                    }
                } else if (!on && recipe.nutrition != null) {
                    viewModel.updateRecipe(index) { it.copy(nutrition = null) }
                }
            },
        )
    }
    if (expanded) {
        recipe.nutrition?.let { n ->
            Column(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    WizardTextField(
                        label = "kcal",
                        value = n.calories,
                        onValueChange = { v -> viewModel.updateRecipe(index) { it.copy(nutrition = n.copy(calories = v)) } },
                        modifier = Modifier.weight(1f),
                    )
                    WizardTextField(
                        label = stringResource(R.string.nutrition_protein),
                        value = n.protein,
                        onValueChange = { v -> viewModel.updateRecipe(index) { it.copy(nutrition = n.copy(protein = v)) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    WizardTextField(
                        label = stringResource(R.string.nutrition_fat),
                        value = n.fat,
                        onValueChange = { v -> viewModel.updateRecipe(index) { it.copy(nutrition = n.copy(fat = v)) } },
                        modifier = Modifier.weight(1f),
                    )
                    WizardTextField(
                        label = stringResource(R.string.nutrition_carbs),
                        value = n.carbs,
                        onValueChange = { v -> viewModel.updateRecipe(index) { it.copy(nutrition = n.copy(carbs = v)) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Step 5 — Übersicht                                                         */
/* ------------------------------------------------------------------------- */

@Composable
fun SummaryStep(
    recipe: EditableRecipe,
    viewModel: ReviewViewModel,
) {
    StepScroll {
        val primary = recipe.images.firstOrNull { it.isPrimary } ?: recipe.images.firstOrNull()
        val imageUrl = primary?.let { viewModel.imageUrlFor(it) }
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Text(
                stringResource(R.string.wizard_summary_no_image),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            recipe.name.ifBlank { "—" },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (recipe.servings.isNotBlank()) {
            Text(
                stringResource(R.string.wizard_summary_servings, recipe.servings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recipe.category.isNotBlank() || recipe.cookbook.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            val context = LocalContext.current
            val catLabel = recipe.category.takeIf { it.isNotBlank() }?.let { RecipeCategories.displayLabel(context, it) }
            Text(
                listOfNotNull(catLabel, recipe.cookbook.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recipe.prepTime.isNotBlank() || recipe.cookTime.isNotBlank()) {
            Text(
                listOfNotNull(
                    recipe.prepTime.takeIf { it.isNotBlank() }?.let { "Vorb. $it" },
                    recipe.cookTime.takeIf { it.isNotBlank() }?.let { "Kochen $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (recipe.ingredients.any { it.name.isNotBlank() }) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider()
            Text(
                stringResource(R.string.review_ingredients),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            recipe.ingredients.filter { it.name.isNotBlank() }.forEach { ing ->
                Text("• ${listOf(ing.quantity, ing.unit, ing.name).filter { it.isNotBlank() }.joinToString(" ")}")
            }
        }
        if (recipe.instructions.any { it.text.isNotBlank() }) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider()
            Text(
                stringResource(R.string.review_instructions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            recipe.instructions.filter { it.text.isNotBlank() }.forEachIndexed { i, s ->
                Text("${i + 1}. ${s.text}")
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Shared widgets                                                             */
/* ------------------------------------------------------------------------- */

@Composable
private fun StepScroll(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        content()
    }
}

@Composable
fun WizardTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.then(Modifier.fillMaxWidth()),
    )
}

@Composable
private fun EmptyHint(text: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
    )
}

/** Coarse-category picker: the fixed [RecipeCategories.KEYS] as toggleable chips (stores the key). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(current: String, onPick: (String) -> Unit) {
    val selected = current.takeIf { it.isNotBlank() }?.let { RecipeCategories.normalizeKey(it) }
    Column {
        Text(
            stringResource(R.string.review_category),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            RecipeCategories.KEYS.forEach { key ->
                FilterChip(
                    selected = selected == key,
                    onClick = { onPick(if (selected == key) "" else key) },
                    label = { Text(stringResource(RecipeCategories.labelRes(key))) },
                )
            }
        }
    }
}
