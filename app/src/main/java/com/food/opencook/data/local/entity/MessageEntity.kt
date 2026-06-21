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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One append-only sync message (the local oplog). [timestamp] is a packed HLC and
 * is globally unique (it embeds the originating node), so it is the primary key
 * and makes inserts idempotent. The materialised tables are projections of these.
 *
 * The SQLite column is `col_key` because `column` is a reserved word.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val timestamp: String,
    val dataset: String,
    val rowId: String,
    @ColumnInfo(name = "col_key") val column: String,
    val value: String,
    val householdId: String? = null,
    val createdAt: Long,
)
