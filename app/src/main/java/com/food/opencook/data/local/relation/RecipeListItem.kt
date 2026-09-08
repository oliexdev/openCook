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


package com.food.opencook.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.RecipeEntity

/**
 * What a recipe *list* needs: the row itself plus its ingredients. Classification, search,
 * availability and planner scoring all work off exactly this much, so they take a
 * [RecipeSummary] instead of a concrete shape and serve both the full [RecipeWithDetails]
 * and the lean [RecipeListItem].
 */
interface RecipeSummary {
    val recipe: RecipeEntity
    val ingredients: List<IngredientEntity>
}

/**
 * A recipe as a list shows it: the row, its ingredients (search / "cookable now") and its
 * photos. Deliberately without instructions and nutrition — the recipe grid, the meal plan
 * and the planner never read them, and a `@Relation` fetches whatever it declares, so every
 * step text of the whole library used to be loaded on every write to any of those tables.
 * Open a recipe and [RecipeWithDetails] is read for that one row.
 */
data class RecipeListItem(
    @Embedded override val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    override val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val images: List<ImageEntity>,
) : RecipeSummary
