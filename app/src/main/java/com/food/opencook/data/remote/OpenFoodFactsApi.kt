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

package com.food.opencook.data.remote

import com.food.opencook.data.remote.dto.OffResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts product lookup. Hits the public API directly (NOT via the
 * self-hosted server) — so it uses a separate Retrofit without [BaseUrlInterceptor]
 * (see the @Named("openfoodfacts") provider in NetworkModule).
 */
interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,product_name_de,brands",
    ): OffResponseDto
}
