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
