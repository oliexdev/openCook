package com.food.opencook.data.remote

import com.food.opencook.data.remote.dto.PendingImportsResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Recipe-import inbox: recipes the browser extension scraped and pushed to the server.
 * An app drains this during its normal sync (see [ImportInboxSyncer]). Claiming is
 * exclusive across devices — [claim] returns 204 to the one winner and 409 to the rest,
 * so each import is materialized exactly once and then propagates via the message log.
 */
interface ImportApi {

    @GET("imports/pending")
    suspend fun pending(@Header("X-Household-Code") code: String): PendingImportsResponseDto

    /** 204 = this device won the claim; 409 = already claimed elsewhere. */
    @POST("imports/{id}/claim")
    suspend fun claim(
        @Path("id") id: String,
        @Header("X-Household-Code") code: String,
    ): Response<Unit>

    @POST("imports/{id}/consume")
    suspend fun consume(
        @Path("id") id: String,
        @Header("X-Household-Code") code: String,
    ): Response<Unit>
}
