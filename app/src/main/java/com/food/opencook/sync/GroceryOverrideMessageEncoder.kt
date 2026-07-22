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

import com.food.opencook.data.local.entity.GroceryOverrideEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Projects a learned "name → aisle" correction into per-field changes for the sync
 * log, so one person's drag teaches every device in the household. rowId is the
 * normalized item name (the entity's natural key); last write wins per HLC, so
 * re-dragging simply overrides the earlier lesson.
 */
object GroceryOverrideMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.GROCERY_OVERRIDES

    fun encode(override: GroceryOverrideEntity): List<FieldChange> = listOf(
        FieldChange(d, override.name, "category", json.encodeToString(String.serializer(), override.category)),
        FieldChange(d, override.name, SyncDatasets.COLUMN_DELETED, "false"),
    )

    /** Forget a lesson (falls back to the keyword heuristic everywhere). */
    fun tombstone(name: String): List<FieldChange> =
        listOf(FieldChange(d, name, SyncDatasets.COLUMN_DELETED, "true"))
}
