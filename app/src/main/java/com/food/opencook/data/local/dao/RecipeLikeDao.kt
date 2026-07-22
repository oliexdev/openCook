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

package com.food.opencook.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.food.opencook.data.local.entity.RecipeLikeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeLikeDao {

    @Upsert
    suspend fun upsert(like: RecipeLikeEntity)

    /** This device's own like state for a recipe (drives the heart toggle). */
    @Query("SELECT * FROM recipe_likes WHERE recipeId = :recipeId AND nodeId = :nodeId")
    fun observe(recipeId: String, nodeId: String): Flow<RecipeLikeEntity?>

    /** Recipes liked by ANY member — the household-wide signal for the planner. */
    @Query("SELECT DISTINCT recipeId FROM recipe_likes WHERE liked = 1")
    suspend fun likedRecipeIds(): List<String>

    /** Reactive [likedRecipeIds] — drives the recipe list's "favorites only" filter. */
    @Query("SELECT DISTINCT recipeId FROM recipe_likes WHERE liked = 1")
    fun observeLikedRecipeIds(): Flow<List<String>>
}
