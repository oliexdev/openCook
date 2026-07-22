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

package com.food.opencook.data.backup

import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.remote.dto.HowToStepDto
import com.food.opencook.data.remote.dto.IngredientDto
import com.food.opencook.data.remote.dto.NutritionDto
import com.food.opencook.data.remote.dto.RecipeDto

/**
 * The inverse of [com.food.opencook.data.remote.mapper.toMappedRecipe]: a stored recipe
 * back into a schema.org/Recipe DTO for `recipes.json`.
 *
 * Written to be readable by *any* schema.org consumer while staying lossless for us:
 *  - `recipeIngredient` holds the flattened "500 g Mehl" lines every other tool expects;
 *  - `openCookIngredients` holds the same list structured, so quantities survive a
 *    round-trip instead of being re-guessed by [IngredientLineParser];
 *  - `identifier` / `openCookId` carry the row ids so a re-import upserts in place.
 */
object RecipeDtoEncoder {

    fun encode(details: RecipeWithDetails, imageRef: String?): RecipeDto {
        val r = details.recipe
        val ingredients = details.ingredients.sortedBy { it.position }
        val instructions = details.instructions.sortedBy { it.position }
        return RecipeDto(
            context = "https://schema.org",
            type = "Recipe",
            identifier = r.id,
            name = r.name,
            description = r.description,
            recipeYield = r.recipeYield,
            openCookServings = r.servings,
            openCookCategory = r.category,
            recipeIngredient = ingredients.map { ingredientLine(it.quantity, it.unit, it.name) },
            openCookIngredients = ingredients.map {
                IngredientDto(quantity = it.quantity, unit = it.unit, name = it.name, openCookId = it.id)
            },
            recipeInstructions = instructions.map {
                HowToStepDto(type = "HowToStep", text = it.text, openCookId = it.id)
            },
            image = listOfNotNull(imageRef),
            openCookNotes = r.notes.toLines(),
            openCookTags = r.tags.toLines(),
            // Only explicit values travel; null (= the lunch+dinner default) stays absent,
            // so the default semantics apply again on the importing device.
            openCookMealTypes = r.mealTypes.toLines(),
            prepTime = r.prepTime,
            cookTime = r.cookTime,
            totalTime = r.totalTime,
            nutrition = details.nutrition?.let {
                NutritionDto(
                    type = "NutritionInformation",
                    calories = it.calories,
                    proteinContent = it.proteinContent,
                    fatContent = it.fatContent,
                    carbohydrateContent = it.carbohydrateContent,
                    fiberContent = it.fiberContent,
                    sugarContent = it.sugarContent,
                    openCookBasis = it.basis,
                )
            },
            cookbook = r.cookbook,
            openCookLastCookedAt = r.lastCookedAt,
            openCookCreatedAt = r.createdAt,
            openCookUpdatedAt = r.updatedAt,
        )
    }

    /** "500 g Mehl" / "2 Eier" / "Salz" — the flattened form for foreign consumers.
     *  Also used by [com.food.opencook.data.export.RecipeMarkdown] so a Markdown export
     *  formats amounts exactly like `recipes.json` does. */
    fun ingredientLine(quantity: Double?, unit: String?, name: String): String =
        listOfNotNull(quantity?.let(::formatQuantity), unit?.takeIf { it.isNotBlank() }, name)
            .joinToString(" ")
            .trim()

    /** Drop a pointless ".0" so amounts read like a recipe, not like a database. */
    fun formatQuantity(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun String?.toLines(): List<String> =
        this?.split("\n")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
}
