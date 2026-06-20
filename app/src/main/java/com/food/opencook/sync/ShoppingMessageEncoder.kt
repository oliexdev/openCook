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
