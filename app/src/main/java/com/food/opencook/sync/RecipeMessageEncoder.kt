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

package com.food.opencook.sync

import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A single field change to be stamped and appended to the message log. */
data class FieldChange(val dataset: String, val rowId: String, val column: String, val value: String)

/** A stampable Hybrid Logical Clock — the only sync capability the repository needs. */
interface Stamper {
    suspend fun stamp(): Hlc
}

/**
 * Projects a recipe (and its child rows) into per-field [FieldChange]s — the
 * "encode" half of sync. Local writes emit these; values are JSON-encoded so the
 * receiver can decode them back into typed columns. Image bytes are intentionally
 * excluded (they sync out-of-band by id).
 */
object RecipeMessageEncoder {

    private val json = Json

    fun encode(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        instructions: List<InstructionEntity>,
        nutrition: NutritionEntity?,
        images: List<ImageEntity> = emptyList(),
    ): List<FieldChange> {
        val out = mutableListOf<FieldChange>()
        val r = SyncDatasets.RECIPES
        out += FieldChange(r, recipe.id, "name", str(recipe.name))
        out += FieldChange(r, recipe.id, "description", str(recipe.description))
        out += FieldChange(r, recipe.id, "recipeYield", str(recipe.recipeYield))
        out += FieldChange(r, recipe.id, "servings", nullableInt(recipe.servings))
        out += FieldChange(r, recipe.id, "category", str(recipe.category))
        out += FieldChange(r, recipe.id, "prepTime", str(recipe.prepTime))
        out += FieldChange(r, recipe.id, "cookTime", str(recipe.cookTime))
        out += FieldChange(r, recipe.id, "totalTime", str(recipe.totalTime))
        out += FieldChange(r, recipe.id, "notes", str(recipe.notes))
        out += FieldChange(r, recipe.id, "tags", str(recipe.tags))
        out += FieldChange(r, recipe.id, "lastCookedAt", str(recipe.lastCookedAt))
        out += FieldChange(r, recipe.id, "cookbook", str(recipe.cookbook))
        // Provenance: the server job id whose original photo produced this recipe.
        // Synced so the recipe→original link survives reinstalls and reaches other devices.
        out += FieldChange(r, recipe.id, "sourcePhotoId", str(recipe.sourcePhotoId))
        out += FieldChange(r, recipe.id, SyncDatasets.COLUMN_DELETED, bool(false))
        // Image syncs out-of-band: only the server-hosted crop's name travels in
        // the log; bytes are fetched via GET /images/{name}. Local-only captures
        // (no remoteName) aren't shared.
        val remoteName = images.firstOrNull { it.isPrimary }?.remoteName
            ?: images.firstOrNull()?.remoteName
        out += FieldChange(r, recipe.id, "imageRef", str(remoteName))

        ingredients.forEach { ing ->
            val d = SyncDatasets.INGREDIENTS
            out += FieldChange(d, ing.id, "recipeId", str(ing.recipeId))
            out += FieldChange(d, ing.id, "position", int(ing.position))
            out += FieldChange(d, ing.id, "quantity", nullableDouble(ing.quantity))
            out += FieldChange(d, ing.id, "unit", str(ing.unit))
            out += FieldChange(d, ing.id, "name", str(ing.name))
        }

        instructions.forEach { step ->
            val d = SyncDatasets.INSTRUCTIONS
            out += FieldChange(d, step.id, "recipeId", str(step.recipeId))
            out += FieldChange(d, step.id, "position", int(step.position))
            out += FieldChange(d, step.id, "text", str(step.text))
        }

        nutrition?.let { n ->
            val d = SyncDatasets.NUTRITION
            out += FieldChange(d, n.recipeId, "calories", str(n.calories))
            out += FieldChange(d, n.recipeId, "proteinContent", str(n.proteinContent))
            out += FieldChange(d, n.recipeId, "fatContent", str(n.fatContent))
            out += FieldChange(d, n.recipeId, "carbohydrateContent", str(n.carbohydrateContent))
            out += FieldChange(d, n.recipeId, "fiberContent", str(n.fiberContent))
            out += FieldChange(d, n.recipeId, "sugarContent", str(n.sugarContent))
            out += FieldChange(d, n.recipeId, "basis", str(n.basis))
        }
        return out
    }

    private fun str(value: String?): String =
        if (value == null) "null" else json.encodeToString(String.serializer(), value)
    private fun int(value: Int): String = value.toString()
    private fun nullableInt(value: Int?): String = value?.toString() ?: "null"
    private fun nullableDouble(value: Double?): String = value?.toString() ?: "null"
    private fun bool(value: Boolean): String = value.toString()
}
