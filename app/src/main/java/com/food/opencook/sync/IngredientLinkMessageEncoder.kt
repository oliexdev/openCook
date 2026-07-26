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

import com.food.opencook.data.local.entity.IngredientLinkEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Projects a learned ingredient relationship into per-field changes for the sync log.
 * The composite `(nameA, nameB)` is the natural key; the two names + kind travel as
 * **fields** (the applier decodes those, never the rowId — exactly like [RecipeLikeMessageEncoder]),
 * so a Unit-Separator joined rowId only has to be collision-free, never parseable.
 */
object IngredientLinkMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.INGREDIENT_LINKS

    private fun rowId(nameA: String, nameB: String) = "$nameA$nameB"

    fun encode(link: IngredientLinkEntity): List<FieldChange> {
        val row = rowId(link.nameA, link.nameB)
        return listOf(
            FieldChange(d, row, "nameA", json.encodeToString(String.serializer(), link.nameA)),
            FieldChange(d, row, "nameB", json.encodeToString(String.serializer(), link.nameB)),
            FieldChange(d, row, "kind", json.encodeToString(String.serializer(), link.kind)),
            FieldChange(d, row, SyncDatasets.COLUMN_DELETED, "false"),
        )
    }

    /** Forget a lesson. Carries the names as fields too, so the applier can delete by pair. */
    fun tombstone(nameA: String, nameB: String): List<FieldChange> {
        val row = rowId(nameA, nameB)
        return listOf(
            FieldChange(d, row, "nameA", json.encodeToString(String.serializer(), nameA)),
            FieldChange(d, row, "nameB", json.encodeToString(String.serializer(), nameB)),
            FieldChange(d, row, SyncDatasets.COLUMN_DELETED, "true"),
        )
    }
}
