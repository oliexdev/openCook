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

package com.food.opencook.repository

import com.food.opencook.data.local.dao.ProductCacheDao
import com.food.opencook.data.local.entity.ProductCacheEntity
import com.food.opencook.data.remote.OpenFoodFactsApi
import com.food.opencook.data.remote.dto.OffProductDto
import javax.inject.Inject
import javax.inject.Singleton

/** A resolved product name for a scanned barcode. */
data class ProductInfo(val barcode: String, val name: String, val brand: String? = null)

@Singleton
class ProductLookupRepository @Inject constructor(
    private val cache: ProductCacheDao,
    private val api: OpenFoodFactsApi,
) {
    /**
     * Resolve a barcode to a product name: local cache first (works offline), then
     * Open Food Facts. Returns null when unknown/offline — the caller falls back to
     * a manual name. A hit is cached so the next scan is instant and offline.
     */
    suspend fun lookup(barcode: String): ProductInfo? {
        cache.get(barcode)?.let { return ProductInfo(it.barcode, it.name, it.brand) }

        val product = runCatching { api.product(barcode) }.getOrNull()?.takeIf { it.status == 1 }?.product
        val name = displayName(product) ?: return null
        cache.upsert(ProductCacheEntity(barcode, name, product?.brands, System.currentTimeMillis()))
        return ProductInfo(barcode, name, product?.brands)
    }

    companion object {
        /** Prefer the German name, then the generic one; blank → null (treated as "not found"). */
        fun displayName(product: OffProductDto?): String? {
            if (product == null) return null
            return (product.productNameDe?.takeIf { it.isNotBlank() }
                ?: product.productName?.takeIf { it.isNotBlank() })
                ?.trim()
        }
    }
}
