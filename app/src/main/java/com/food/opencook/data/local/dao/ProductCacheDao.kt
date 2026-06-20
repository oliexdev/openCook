package com.food.opencook.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.food.opencook.data.local.entity.ProductCacheEntity

@Dao
interface ProductCacheDao {

    @Query("SELECT * FROM product_cache WHERE barcode = :barcode")
    suspend fun get(barcode: String): ProductCacheEntity?

    @Upsert
    suspend fun upsert(product: ProductCacheEntity)

    /** Cached product names — one source for the add-field autocomplete. */
    @Query("SELECT DISTINCT name FROM product_cache")
    suspend fun distinctNames(): List<String>
}
