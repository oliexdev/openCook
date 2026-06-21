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
