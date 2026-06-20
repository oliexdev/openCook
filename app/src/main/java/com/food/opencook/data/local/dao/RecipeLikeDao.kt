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
}
