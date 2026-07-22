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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.image.ImageEditor
import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.SaveResult
import com.food.opencook.repository.SuggestionRepository
import com.food.opencook.util.IngredientCorrection
import com.food.opencook.ui.navigation.Routes
import com.food.opencook.util.DurationFormat
import com.food.opencook.util.MealTypes
import com.food.opencook.util.Numbers
import com.food.opencook.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Linear wizard the editor walks the user through. Order matches array index for the
 *  progress indicator (BASICS = step 1 of 5, SUMMARY = step 5 of 5). */
enum class WizardStep { BASICS, INGREDIENTS, STEPS, DETAILS, SUMMARY }

/** Editable in-memory copies of the detected recipes; mapped back to entities on save. */
data class EditableRecipe(
    val id: String,
    val sourcePhotoId: String?,
    val createdAt: Long,
    val name: String,
    val servings: String,
    val category: String,
    /** Selected [com.food.opencook.util.MealTypes] keys; empty = unset (default applies). */
    val mealTypes: List<String>,
    val prepTime: String,
    val cookTime: String,
    val notes: String,
    val cookbook: String,
    val ingredients: List<EditableIngredient>,
    val instructions: List<EditableInstruction>,
    val nutrition: EditableNutrition?,
    val images: List<ImageEntity>,
    /** Search tags, newline-joined (AI-assigned, user-correctable in the details step). */
    val tags: String? = null,
    // Fields the editor doesn't expose but must not destroy: persist() rebuilds the
    // whole entity, so anything missing here would be wiped on every edit (and the
    // wipe would sync). Carried through verbatim.
    val lastCookedAt: String? = null,
    val totalTime: String? = null,
)

/**
 * [id] is null for a freshly added row; existing rows keep their id so edits sync in place.
 * [suggestion]/[autoCorrected] are UI-only review hints from the ingredient corrector
 * (never persisted): a non-null [suggestion] offers a tap-to-apply fix, [autoCorrected] marks
 * a name the corrector already snapped to a known term.
 */
data class EditableIngredient(
    val id: String?,
    val quantity: String,
    val unit: String,
    val name: String,
    val suggestion: String? = null,
    val autoCorrected: Boolean = false,
)

data class EditableInstruction(val id: String?, val text: String)

data class EditableNutrition(
    val calories: String,
    val protein: String,
    val fat: String,
    val carbs: String,
    val basis: String,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: RecipeRepository,
    private val suggestionRepository: SuggestionRepository,
    private val imageStore: ImageStore,
    private val imageEditor: ImageEditor,
    settings: SettingsRepository,
) : ViewModel() {

    // Review-of-scan(s) uses jobId; editing one saved recipe uses recipeId.
    private val jobId: String? = savedStateHandle[Routes.ARG_JOB_ID]
    private val recipeId: String? = savedStateHandle[Routes.ARG_RECIPE_ID]

    /** null while loading; empty list means nothing was detected. */
    private val _recipes = MutableStateFlow<List<EditableRecipe>?>(null)
    val recipes: StateFlow<List<EditableRecipe>?> = _recipes.asStateFlow()

    /** Transient user message (e.g. a duplicate-name warning); the screen shows + clears it. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val serverBaseUrl: StateFlow<String?> =
        settings.serverUrl.map { it?.trimEnd('/') }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val manual = jobId == Routes.JOB_ID_NEW
            val loaded = when {
                recipeId != null -> listOfNotNull(repository.getRecipeOnce(recipeId))
                manual -> emptyList()
                jobId == Routes.JOB_ID_ALL -> repository.getUnreviewedRecipes()
                jobId != null -> repository.getRecipesForJob(jobId)
                else -> emptyList()
            }
            val editables = loaded.map { it.toEditable() }
            _recipes.value = when {
                // Manual create: start with one blank draft so the editor has something to fill in.
                manual && editables.isEmpty() -> listOf(blankDraft())
                // Freshly-scanned recipes: run names through the offline corrector so the review
                // screen can offer typo fixes; never touch an existing saved recipe.
                recipeId == null -> {
                    val corrector = IngredientCorrection.corrector(suggestionRepository.pool())
                    editables.map { it.withCorrections(corrector) }
                }
                else -> editables
            }
            // Reviewing freshly-scanned recipes also acknowledges them (clears the
            // status strip); editing an existing recipe / starting from scratch doesn't.
            if (recipeId == null && !manual) repository.acknowledgeFinishedJobs()
        }
    }

    private fun blankDraft(): EditableRecipe {
        val now = System.currentTimeMillis()
        return EditableRecipe(
            id = UUID.randomUUID().toString(),
            sourcePhotoId = null,
            createdAt = now,
            name = "",
            servings = "",
            category = "",
            // The classic hot-meal slots pre-selected — same meaning as "unset".
            mealTypes = MealTypes.DEFAULT,
            prepTime = "",
            cookTime = "",
            notes = "",
            cookbook = "",
            ingredients = listOf(EditableIngredient(id = null, quantity = "", unit = "", name = "")),
            instructions = listOf(EditableInstruction(id = null, text = "")),
            nutrition = null,
            images = emptyList(),
        )
    }

    /** Coil model for a crop: a local [java.io.File] or a server URL; null if neither. */
    fun imageUrlFor(image: ImageEntity): Any? = when {
        image.localPath != null -> java.io.File(image.localPath)
        image.remoteName != null -> serverBaseUrl.value?.let { "$it/images/${image.remoteName}" }
        else -> null
    }

    fun updateRecipe(index: Int, transform: (EditableRecipe) -> EditableRecipe) {
        _recipes.update { list ->
            list?.mapIndexed { i, r -> if (i == index) transform(r) else r }
        }
    }

    fun addIngredient(index: Int) = updateRecipe(index) {
        it.copy(ingredients = it.ingredients + EditableIngredient(id = null, quantity = "", unit = "", name = ""))
    }

    fun removeIngredient(index: Int, ingredientIndex: Int) = updateRecipe(index) {
        it.copy(ingredients = it.ingredients.filterIndexed { i, _ -> i != ingredientIndex })
    }

    /** Accept the corrector's "Meinten Sie …?" suggestion for one ingredient. */
    fun applySuggestion(index: Int, ingredientIndex: Int) = updateRecipe(index) { r ->
        r.copy(ingredients = r.ingredients.mapIndexed { i, ing ->
            if (i == ingredientIndex && ing.suggestion != null) {
                ing.copy(name = ing.suggestion, suggestion = null, autoCorrected = false)
            } else {
                ing
            }
        })
    }

    fun addStep(index: Int) = updateRecipe(index) {
        it.copy(instructions = it.instructions + EditableInstruction(id = null, text = ""))
    }

    fun removeStep(index: Int, stepIndex: Int) = updateRecipe(index) {
        it.copy(instructions = it.instructions.filterIndexed { i, _ -> i != stepIndex })
    }

    /** Swap two instruction rows so the user can reorder steps without delete-and-re-add. */
    fun moveStep(index: Int, from: Int, to: Int) = updateRecipe(index) { r ->
        if (from !in r.instructions.indices || to !in r.instructions.indices || from == to) return@updateRecipe r
        val mutable = r.instructions.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        r.copy(instructions = mutable)
    }

    // --- Wizard navigation (per pager page) ---

    private val _stepByPage = MutableStateFlow<Map<Int, WizardStep>>(emptyMap())
    val stepByPage: StateFlow<Map<Int, WizardStep>> = _stepByPage.asStateFlow()

    fun stepFor(page: Int): WizardStep = _stepByPage.value[page] ?: WizardStep.BASICS

    fun setStep(page: Int, step: WizardStep) {
        _stepByPage.update { it + (page to step) }
    }

    /** Attach a gallery image as the recipe's primary photo (non-blocking helper). */
    fun attachImage(index: Int, uri: Uri) {
        viewModelScope.launch {
            attachLocalImage(index, imageStore.saveFromUri(uri))
        }
    }

    /** The primary image of the recipe at [index], but only if it has a local file we
     *  can edit (rotate/crop need bytes on disk; a not-yet-downloaded remote image has none). */
    private fun editablePrimary(index: Int): ImageEntity? {
        val recipe = _recipes.value?.getOrNull(index) ?: return null
        val primary = recipe.images.firstOrNull { it.isPrimary } ?: recipe.images.firstOrNull()
        return primary?.takeIf { it.localPath != null }
    }

    /** Whether the recipe at [index] has a locally-editable primary image (drives the UI). */
    fun canEditImage(index: Int): Boolean = editablePrimary(index) != null

    /** Rotate the primary image 90° clockwise and re-attach it (re-uploads on next sync). */
    fun rotateImage(index: Int) {
        val image = editablePrimary(index) ?: return
        viewModelScope.launch {
            attachLocalImage(index, imageEditor.rotate90(image.localPath!!))
        }
    }

    /** Crop the primary image to a fractional rectangle (0..1) and re-attach it. */
    fun cropImage(index: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val image = editablePrimary(index) ?: return
        viewModelScope.launch {
            attachLocalImage(index, imageEditor.crop(image.localPath!!, left, top, right, bottom))
        }
    }

    /** Attach an already-saved local file (e.g. a camera capture) as primary photo. */
    fun attachLocalImage(index: Int, path: String) {
        val recipeId = _recipes.value?.getOrNull(index)?.id ?: return
        val image = ImageEntity(
            id = UUID.randomUUID().toString(),
            recipeId = recipeId,
            position = 0,
            remoteName = null,
            localPath = path,
            isPrimary = true,
        )
        updateRecipe(index) { it.copy(images = listOf(image)) }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val list = _recipes.value ?: return@launch
            var duplicates = 0
            list.forEach { editable -> if (persist(editable, now) == SaveResult.Duplicate) duplicates++ }
            if (duplicates > 0) {
                // Don't close — tell the user a same-named recipe already exists.
                _message.value = if (duplicates == 1) context.getString(R.string.review_duplicate_one)
                else context.getString(R.string.review_duplicate_many, duplicates)
            } else {
                onSaved()
            }
        }
    }

    private suspend fun persist(e: EditableRecipe, now: Long): SaveResult {
        val servingsNum = e.servings.trim().toIntOrNull()
        val recipe = RecipeEntity(
            id = e.id,
            name = e.name.ifBlank { null },
            // Keep the numeric servings as the source of truth; the UI renders a localized
            // "N servings" label from it (no German string baked into stored data).
            recipeYield = null,
            servings = servingsNum,
            category = e.category.ifBlank { null },
            // Deselecting everything stores null — the "lunch + dinner" default applies again.
            mealTypes = MealTypes.toStored(e.mealTypes),
            notes = e.notes.ifBlank { null },
            cookbook = e.cookbook.ifBlank { null },
            prepTime = DurationFormat.toIso(e.prepTime),
            cookTime = DurationFormat.toIso(e.cookTime),
            totalTime = e.totalTime,
            tags = e.tags,
            lastCookedAt = e.lastCookedAt,
            sourcePhotoId = e.sourcePhotoId,
            createdAt = e.createdAt,
            updatedAt = now,
        )
        val ingredients = e.ingredients
            .filter { it.quantity.isNotBlank() || it.name.isNotBlank() }
            .mapIndexed { i, ing ->
                IngredientEntity(
                    id = ing.id ?: UUID.randomUUID().toString(), // reuse stable id on edit
                    recipeId = e.id,
                    position = i,
                    quantity = ing.quantity.trim().replace(',', '.').toDoubleOrNull(),
                    unit = ing.unit.ifBlank { null },
                    name = ing.name,
                )
            }
        val instructions = e.instructions
            .filter { it.text.isNotBlank() }
            .mapIndexed { i, step ->
                InstructionEntity(step.id ?: UUID.randomUUID().toString(), e.id, i, step.text)
            }
        val nutrition = e.nutrition?.toEntity(e.id)
        return repository.saveRecipe(recipe, ingredients, instructions, nutrition, e.images)
    }
}

/** Run each ingredient name through the offline corrector, attaching review hints. */
private fun EditableRecipe.withCorrections(corrector: IngredientCorrection.Corrector): EditableRecipe =
    copy(
        ingredients = ingredients.map { ing ->
            if (ing.name.isBlank()) {
                ing
            } else {
                val r = corrector.correct(ing.name)
                ing.copy(name = r.name, suggestion = r.suggestion, autoCorrected = r.autoCorrected)
            }
        },
    )

private fun RecipeWithDetails.toEditable() = EditableRecipe(
    id = recipe.id,
    sourcePhotoId = recipe.sourcePhotoId,
    createdAt = recipe.createdAt,
    name = recipe.name.orEmpty(),
    servings = recipe.servings?.toString().orEmpty(),
    category = recipe.category.orEmpty(),
    // Null resolves to the default, so the chips show "lunch + dinner" pre-selected.
    mealTypes = MealTypes.fromStored(recipe.mealTypes),
    prepTime = DurationFormat.toHuman(recipe.prepTime),
    cookTime = DurationFormat.toHuman(recipe.cookTime),
    notes = recipe.notes.orEmpty(),
    cookbook = recipe.cookbook.orEmpty(),
    tags = recipe.tags,
    lastCookedAt = recipe.lastCookedAt,
    totalTime = recipe.totalTime,
    ingredients = ingredients.sortedBy { it.position }
        .map { EditableIngredient(it.id, Numbers.formatQuantity(it.quantity).orEmpty(), it.unit.orEmpty(), it.name) },
    instructions = instructions.sortedBy { it.position }.map { EditableInstruction(it.id, it.text) },
    nutrition = nutrition?.let {
        EditableNutrition(
            calories = it.calories.orEmpty(),
            protein = it.proteinContent.orEmpty(),
            fat = it.fatContent.orEmpty(),
            carbs = it.carbohydrateContent.orEmpty(),
            basis = it.basis.orEmpty(),
        )
    },
    images = images.sortedByDescending { it.isPrimary },
)

private fun EditableNutrition.toEntity(recipeId: String): NutritionEntity? {
    val hasAny = listOf(calories, protein, fat, carbs).any { it.isNotBlank() }
    if (!hasAny) return null
    return NutritionEntity(
        recipeId = recipeId,
        calories = calories.ifBlank { null },
        proteinContent = protein.ifBlank { null },
        fatContent = fat.ifBlank { null },
        carbohydrateContent = carbs.ifBlank { null },
        basis = basis.ifBlank { null },
    )
}
