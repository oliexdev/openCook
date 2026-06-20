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
