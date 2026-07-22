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

/**
 * One append-only change to a single field — the unit of sync.
 *
 * [timestamp] is a packed [Hlc]. [value] is the JSON-encoded column value (null
 * is encoded as the JSON string "null"; a deletion is a tombstone, see
 * [SyncDatasets]). Household scoping lives at the storage/transport layer, not
 * in the merge core, so the engine stays purely about convergence.
 */
data class Message(
    val timestamp: String,
    val dataset: String,
    val rowId: String,
    val column: String,
    val value: String,
)

/** Well-known dataset/column names shared by the materialised tables and the log. */
object SyncDatasets {
    const val RECIPES = "recipes"
    const val INGREDIENTS = "ingredients"
    const val INSTRUCTIONS = "instructions"
    const val NUTRITION = "nutrition"
    const val SHOPPING = "shopping"
    const val PANTRY = "pantry"
    const val MEALPLAN = "mealplan"
    const val MEAL_DAYS = "mealdays"
    const val RECIPE_LIKES = "recipe_likes"
    const val GROCERY_OVERRIDES = "grocery_overrides"

    /** Boolean column flipped to true to tombstone (delete) a row across all devices. */
    const val COLUMN_DELETED = "_deleted"
}
