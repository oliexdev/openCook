package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Printed nutrition for a recipe. Modelled as a 1:1 child keyed by recipeId so
 * "no nutrition" is simply the absence of a row. All values are stored exactly
 * as displayed ("560 kcal", "17 g") — the AI must NEVER estimate these, so we
 * keep them as faithful strings and never parse/recompute.
 */
@Entity(
    tableName = "nutrition",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NutritionEntity(
    @PrimaryKey val recipeId: String,
    val calories: String? = null,
    val proteinContent: String? = null,
    val fatContent: String? = null,
    val carbohydrateContent: String? = null,
    val fiberContent: String? = null,
    val sugarContent: String? = null,
    /** e.g. "pro Portion" / "pro 100 g". */
    val basis: String? = null,
)
