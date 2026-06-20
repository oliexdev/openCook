package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A staple the household keeps in stock; used to skip already-owned ingredients. */
@Entity(tableName = "pantry_items")
data class PantryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)
