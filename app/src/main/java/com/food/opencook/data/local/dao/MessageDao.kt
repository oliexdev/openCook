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

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}
