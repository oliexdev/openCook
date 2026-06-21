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
