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
