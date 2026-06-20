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
