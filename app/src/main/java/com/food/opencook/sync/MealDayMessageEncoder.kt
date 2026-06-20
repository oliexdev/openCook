package com.food.opencook.sync

import com.food.opencook.data.local.entity.MealDayEntity
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Projects a meal-day flag (skip) into per-field changes for the sync log. */
object MealDayMessageEncoder {
    private val json = Json
    private val d = SyncDatasets.MEAL_DAYS

    fun encode(day: MealDayEntity): List<FieldChange> = listOf(
        FieldChange(d, day.date, "date", json.encodeToString(String.serializer(), day.date)),
        FieldChange(d, day.date, "skipped", day.skipped.toString()),
        FieldChange(d, day.date, SyncDatasets.COLUMN_DELETED, "false"),
    )
}
