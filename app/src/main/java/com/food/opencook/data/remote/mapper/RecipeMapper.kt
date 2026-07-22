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

package com.food.opencook.data.remote.mapper

import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.remote.dto.NutritionDto
import com.food.opencook.data.remote.dto.RecipeDto
import com.food.opencook.util.MealTypes
import com.food.opencook.util.RecipeCategories
import java.util.UUID

/** A recipe plus its child rows, ready to insert in one transaction. */
data class MappedRecipe(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientEntity>,
    val instructions: List<InstructionEntity>,
    val nutrition: NutritionEntity?,
    val images: List<ImageEntity>,
)

/**
 * Map an extracted schema.org [RecipeDto] into Room entities. Fresh UUIDs are
 * generated for every row (stable IDs the app owns) — except where the DTO carries
 * openCook's own ids ([RecipeDto.identifier], [IngredientDto.openCookId],
 * [HowToStepDto.openCookId]), which a backup round-trip reuses so re-importing
 * upserts the existing rows instead of duplicating them. Structured
 * [RecipeDto.openCookIngredients] are preferred; the flattened
 * [RecipeDto.recipeIngredient] strings are used only as a fallback.
 */
fun RecipeDto.toMappedRecipe(
    sourcePhotoId: String?,
    now: Long,
    idFactory: () -> String = { UUID.randomUUID().toString() },
): MappedRecipe {
    val recipeId = identifier?.takeIf { it.isNotBlank() } ?: idFactory()

    val drafts = if (openCookIngredients.isNotEmpty()) {
        openCookIngredients.map { IngredientDraft(it.quantity, it.unit, it.name, it.openCookId) }
    } else {
        // Fallback: flattened "recipeIngredient" strings have no structure.
        recipeIngredient.map { IngredientDraft(null, null, it, null) }
    }
    val ingredients = dedupeIngredients(drafts).mapIndexed { index, draft ->
        IngredientEntity(
            id = draft.id?.takeIf { it.isNotBlank() } ?: idFactory(),
            recipeId = recipeId,
            position = index,
            quantity = draft.quantity,
            unit = draft.unit,
            name = draft.name,
        )
    }

    val instructions = recipeInstructions.mapIndexed { index, step ->
        InstructionEntity(
            id = step.openCookId?.takeIf { it.isNotBlank() } ?: idFactory(),
            recipeId = recipeId,
            position = index,
            text = stripStepNumber(step.text),
        )
    }

    val images = image.mapIndexed { index, remoteName ->
        ImageEntity(
            id = idFactory(),
            recipeId = recipeId,
            position = index,
            remoteName = remoteName,
            localPath = null,
            isPrimary = index == 0,
        )
    }

    return MappedRecipe(
        recipe = RecipeEntity(
            id = recipeId,
            name = name,
            description = description?.takeIf { it.isNotBlank() },
            // Keep the source yield string if the recipe carried one; otherwise leave it null
            // and let the UI render a localized "N servings" label from the numeric servings.
            recipeYield = recipeYield,
            servings = openCookServings,
            category = openCookCategory?.let { RecipeCategories.normalizeKey(it) },
            mealTypes = MealTypes.toStored(openCookMealTypes.mapNotNull { MealTypes.normalizeKey(it) }),
            notes = openCookNotes.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            tags = openCookTags.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            lastCookedAt = openCookLastCookedAt?.takeIf { it.isNotBlank() },
            cookbook = cookbook?.takeIf { it.isNotBlank() },
            prepTime = prepTime,
            cookTime = cookTime,
            totalTime = totalTime,
            sourcePhotoId = sourcePhotoId,
            householdId = null,
            // A backup restores the original timestamps so "recently added" ordering and
            // the meal planner's recency signals survive; fresh imports use `now`.
            createdAt = openCookCreatedAt ?: now,
            updatedAt = openCookUpdatedAt ?: now,
        ),
        ingredients = ingredients,
        instructions = instructions,
        nutrition = nutrition?.toEntity(recipeId),
        images = images,
    )
}

private val STEP_NUMBER_REGEX = Regex("""^\s*\d{1,2}\s*[.)]\s*""")

/**
 * Drop a leading printed step number ("1.", "2)") from an instruction. The UI
 * numbers steps itself ("${i + 1}."), so a number kept in the text renders as
 * "1. 1. ...". Never blanks a step that is only a number.
 */
private fun stripStepNumber(text: String): String {
    val stripped = STEP_NUMBER_REGEX.replace(text, "").trim()
    return stripped.ifEmpty { text }
}

private data class IngredientDraft(
    val quantity: Double?,
    val unit: String?,
    val name: String,
    val id: String? = null,
)

private fun normalizeUnit(unit: String?): String? =
    unit?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

/**
 * Collapse exact-duplicate ingredients (same name, case-insensitive and trimmed).
 *
 * Deliberately conservative — a safety net behind the extraction prompt, which is
 * what actually prevents duplicates by only reading the ingredient list (never the
 * step text). Entries merge only on identical name AND matching unit; a differing
 * unit keeps them separate rather than risk corrupting amounts. When merging, an
 * identical quantity means a true repeat ("1 rote Paprikaschote" listed twice) and
 * collapses to one; differing quantities are summed (a split "100 g" + "50 g").
 * No fuzzy matching, so legitimate variants ("weiße" vs. "rote Zwiebel") never fuse.
 */
private fun dedupeIngredients(drafts: List<IngredientDraft>): List<IngredientDraft> {
    val out = mutableListOf<IngredientDraft>()
    for (draft in drafts) {
        val key = draft.name.trim().lowercase()
        if (key.isEmpty()) {
            out.add(draft)
            continue
        }
        val index = out.indexOfFirst {
            it.name.trim().lowercase() == key && normalizeUnit(it.unit) == normalizeUnit(draft.unit)
        }
        if (index == -1) {
            out.add(draft)
        } else {
            val existing = out[index]
            val mergedQuantity = when {
                existing.quantity == null -> draft.quantity
                draft.quantity == null -> existing.quantity
                existing.quantity == draft.quantity -> existing.quantity  // true repeat
                else -> existing.quantity + draft.quantity                // split amount
            }
            out[index] = existing.copy(quantity = mergedQuantity)
        }
    }
    return out
}

private fun NutritionDto.toEntity(recipeId: String): NutritionEntity? {
    val entity = NutritionEntity(
        recipeId = recipeId,
        calories = calories,
        proteinContent = proteinContent,
        fatContent = fatContent,
        carbohydrateContent = carbohydrateContent,
        fiberContent = fiberContent,
        sugarContent = sugarContent,
        basis = openCookBasis,
    )
    // Drop a nutrition block that carried no actual values.
    val hasAnyValue = listOf(
        calories, proteinContent, fatContent, carbohydrateContent, fiberContent, sugarContent,
    ).any { !it.isNullOrBlank() }
    return if (hasAnyValue) entity else null
}
