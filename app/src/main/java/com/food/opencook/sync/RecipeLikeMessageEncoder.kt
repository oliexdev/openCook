package com.food.opencook.sync

import com.food.opencook.data.local.entity.RecipeLikeEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Projects one member's recipe "like" into per-field changes. The row id combines
 * recipe and member ("$recipeId:$nodeId") so each member's like is an independent
 * row; the recipeId/nodeId are also carried as fields for decoding.
 */
object RecipeLikeMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.RECIPE_LIKES

    fun encode(like: RecipeLikeEntity): List<FieldChange> {
        val row = "${like.recipeId}:${like.nodeId}"
        return listOf(
            FieldChange(d, row, "recipeId", json.encodeToString(String.serializer(), like.recipeId)),
            FieldChange(d, row, "nodeId", json.encodeToString(String.serializer(), like.nodeId)),
            FieldChange(d, row, "liked", like.liked.toString()),
            FieldChange(d, row, SyncDatasets.COLUMN_DELETED, "false"),
        )
    }
}
