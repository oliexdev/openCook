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
