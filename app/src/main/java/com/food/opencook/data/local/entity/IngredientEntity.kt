package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One structured ingredient line: a numeric [quantity] + [unit] + [name]
 * (e.g. 400 / "g" / "Nudeln"). [quantity]/[unit] are null for non-quantifiable
 * items ("etwas Salz"). Structured (not free text) so the meal planner can scale
 * servings and the shopping list can sum same-unit amounts.
 */
@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val position: Int,
    val quantity: Double?,
    val unit: String?,
    val name: String,
)
