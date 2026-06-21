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
import androidx.room.PrimaryKey

/**
 * One shopping-list line. Client-generated UUID id so it syncs like recipes.
 * [quantity] + [unit] are numeric/structured so same-unit items can be summed
 * (400 g + 100 g = 500 g); both null for a plain manual entry.
 */
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val text: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val checked: Boolean = false,
    val position: Int = 0,
    /** Provenance when generated from the meal plan: which recipe + which day. Null
     * for manual entries. Powers the "ingredient not found → replace the dish" flow. */
    val sourceRecipeId: String? = null,
    val sourceDate: String? = null,
    /** True when the user added this line by hand — it then always shows, even if the
     * pantry covers the name (manual entry wins over "already in stock"). A manual touch
     * on a consolidated line (manual + recipe re-add) latches this on. */
    val manual: Boolean = false,
    /** Comma-separated, de-duplicated recipe ids of every dish that contributed to this
     * (consolidated) line, for the "needed for …" label. Null for pure manual entries.
     * Distinct from [sourceRecipeId], which keeps the single first-dish provenance the
     * replace-the-dish flow relies on. */
    val sourceRecipeIds: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
