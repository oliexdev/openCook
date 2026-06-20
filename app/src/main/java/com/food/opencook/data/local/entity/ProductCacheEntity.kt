package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of barcode → product name lookups (Open Food Facts). Purely a network
 * cache so a re-scanned product is recognised offline; not synced (barcodes are universal).
 */
@Entity(tableName = "product_cache")
data class ProductCacheEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String? = null,
    val updatedAt: Long,
)
