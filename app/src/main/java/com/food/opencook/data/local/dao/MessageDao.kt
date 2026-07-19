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

package com.food.opencook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.food.opencook.data.local.entity.MessageEntity

@Dao
interface MessageDao {

    /** Idempotent: a message already seen (same HLC) is ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /** The owning clock of a field = the newest message for it (null if none). */
    @Query("SELECT MAX(timestamp) FROM messages WHERE dataset = :dataset AND rowId = :rowId AND col_key = :column")
    suspend fun maxTimestamp(dataset: String, rowId: String, column: String): String?

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun all(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE timestamp > :cursor ORDER BY timestamp ASC")
    suspend fun since(cursor: String): List<MessageEntity>

    /** All messages for one row, to recompute its winning field values on projection. */
    @Query("SELECT * FROM messages WHERE dataset = :dataset AND rowId = :rowId")
    suspend fun forRow(dataset: String, rowId: String): List<MessageEntity>

    /** Which of these timestamps are already in the log. Lets the applier skip messages
     *  it has seen — peers push their whole log each round, and re-projecting every row
     *  inside one big transaction stalled all other DB writes (UI froze during sync). */
    @Query("SELECT timestamp FROM messages WHERE timestamp IN (:timestamps)")
    suspend fun existingTimestamps(timestamps: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}
