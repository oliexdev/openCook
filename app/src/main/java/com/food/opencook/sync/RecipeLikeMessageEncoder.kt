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
