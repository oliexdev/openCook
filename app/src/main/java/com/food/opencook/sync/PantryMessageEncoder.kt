package com.food.opencook.sync

import com.food.opencook.data.local.entity.PantryItemEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Projects a pantry item into per-field changes for the sync log. */
object PantryMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.PANTRY

    fun encode(item: PantryItemEntity): List<FieldChange> = listOf(
        FieldChange(d, item.id, "name", json.encodeToString(String.serializer(), item.name)),
        FieldChange(d, item.id, SyncDatasets.COLUMN_DELETED, "false"),
    )

    fun tombstone(itemId: String): List<FieldChange> =
        listOf(FieldChange(d, itemId, SyncDatasets.COLUMN_DELETED, "true"))
}
