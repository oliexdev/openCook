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
