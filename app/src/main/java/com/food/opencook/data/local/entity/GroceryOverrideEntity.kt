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

package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-taught grocery-aisle correction: "this item name belongs in that aisle".
 * Written when someone drags a shopping/pantry item into another category group;
 * consulted before the keyword heuristic so the whole household's lists learn from
 * one person's correction (the table syncs like the rest of the data). The lesson is
 * name-scoped on purpose — shopping rows die at checkout, so item-level state would
 * be forgotten by next week's list.
 *
 * Keyed by the **normalized** item name (trimmed, lower-cased) so "Lauch" and
 * "lauch " teach the same row. [category] holds a [com.food.opencook.util.GroceryCategory]
 * enum name; unknown values (from a newer app version) are ignored at read time.
 */
@Entity(tableName = "grocery_overrides")
data class GroceryOverrideEntity(
    @PrimaryKey val name: String,
    val category: String,
    val updatedAt: Long,
)
