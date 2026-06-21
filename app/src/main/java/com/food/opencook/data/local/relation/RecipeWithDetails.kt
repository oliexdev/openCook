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
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity

/**
 * A recipe with all of its child rows, read in one shot. The lists are not
 * guaranteed ordered by Room, so consumers sort by `position` (see DAO/mapper).
 */
data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val instructions: List<InstructionEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val images: List<ImageEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val nutrition: NutritionEntity?,
)
