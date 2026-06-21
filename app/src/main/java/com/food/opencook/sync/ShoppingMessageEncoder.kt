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

import com.food.opencook.data.local.entity.ShoppingItemEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Projects a shopping item into per-field, JSON-encoded changes for the log. */
object ShoppingMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.SHOPPING

    fun encode(item: ShoppingItemEntity): List<FieldChange> = listOf(
        FieldChange(d, item.id, "text", str(item.text)),
        FieldChange(d, item.id, "quantity", item.quantity?.toString() ?: "null"),
        FieldChange(d, item.id, "unit", str(item.unit)),
        FieldChange(d, item.id, "checked", item.checked.toString()),
        FieldChange(d, item.id, "position", item.position.toString()),
        FieldChange(d, item.id, "sourceRecipeId", str(item.sourceRecipeId)),
        FieldChange(d, item.id, "sourceDate", str(item.sourceDate)),
        FieldChange(d, item.id, "manual", item.manual.toString()),
        FieldChange(d, item.id, "sourceRecipeIds", str(item.sourceRecipeIds)),
        FieldChange(d, item.id, SyncDatasets.COLUMN_DELETED, "false"),
    )

    fun tombstone(itemId: String): List<FieldChange> =
        listOf(FieldChange(d, itemId, SyncDatasets.COLUMN_DELETED, "true"))

    private fun str(value: String?): String =
        if (value == null) "null" else json.encodeToString(String.serializer(), value)
}
