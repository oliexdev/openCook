package com.food.opencook.sync

import com.food.opencook.data.local.entity.MealPlanEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Projects a meal-plan entry into per-field changes for the sync log. */
object MealPlanMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.MEALPLAN

    fun encode(entry: MealPlanEntity): List<FieldChange> {
        val out = mutableListOf(
            FieldChange(d, entry.id, "date", json.encodeToString(String.serializer(), entry.date)),
            FieldChange(d, entry.id, "recipeId", json.encodeToString(String.serializer(), entry.recipeId)),
            FieldChange(d, entry.id, "pinned", entry.pinned.toString()),
            FieldChange(d, entry.id, SyncDatasets.COLUMN_DELETED, "false"),
        )
        // Score breakdown travels with the plan so any household device can show "why
        // this dish?". Manually-added entries have no reasons; emit explicit "null" so
        // a re-roll's old reasons can be cleanly overwritten under LWW.
        val reasonsValue = entry.reasonsJson?.let { json.encodeToString(String.serializer(), it) } ?: "null"
        out += FieldChange(d, entry.id, "reasonsJson", reasonsValue)
        // Per-day "confirmed cooked" date; explicit "null" so un-marking (or a roll-forward
        // that resets it) overwrites a previous date cleanly under LWW.
        val cookedValue = entry.cookedAt?.let { json.encodeToString(String.serializer(), it) } ?: "null"
        out += FieldChange(d, entry.id, "cookedAt", cookedValue)
        return out
    }

    fun tombstone(entryId: String): List<FieldChange> =
        listOf(FieldChange(d, entryId, SyncDatasets.COLUMN_DELETED, "true"))
}
