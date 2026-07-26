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

/**
 * A user-taught relationship between two ingredient names, learned from the shopping list's
 * "brauch ich doch" chip: today only [kind] `"distinct"` ("these two are NOT the same
 * product", so a pantry item must not silently cover the recipe ingredient). The `kind`
 * column stays for forward-compatibility (a future positive link) — unknown kinds are
 * ignored at read time.
 *
 * Both names are stored **normalized (lower-cased, trimmed) and sorted** so `(a,b)` and
 * `(b,a)` collapse to one row; the pair is the natural composite key. Name-scoped like
 * `GroceryOverrideEntity`, and it syncs the same way, so one person's lesson teaches the
 * whole household.
 */
@Entity(tableName = "ingredient_links", primaryKeys = ["nameA", "nameB"])
data class IngredientLinkEntity(
    val nameA: String,
    val nameB: String,
    val kind: String,
    val updatedAt: Long,
)
