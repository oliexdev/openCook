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

/**
 * One household member's "liked" flag for a recipe. There are no accounts, so a
 * member is identified by their anonymous sync [nodeId] — this keeps likes
 * **independent per member**: one person un-liking can't overwrite another's like
 * (a single LWW field on the recipe couldn't do that). Syncs household-wide.
 */
@Entity(tableName = "recipe_likes", primaryKeys = ["recipeId", "nodeId"])
data class RecipeLikeEntity(
    val recipeId: String,
    val nodeId: String,
    val liked: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
