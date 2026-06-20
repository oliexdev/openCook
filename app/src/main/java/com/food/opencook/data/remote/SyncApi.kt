package com.food.opencook.data.remote

import com.food.opencook.data.remote.dto.CreateHouseholdRequest
import com.food.opencook.data.remote.dto.HouseholdDto
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.data.remote.dto.JoinHouseholdRequest
import com.food.opencook.data.remote.dto.PatchHouseholdRequest
import com.food.opencook.data.remote.dto.ImageUploadResponseDto
import com.food.opencook.data.remote.dto.SyncRequestDto
import com.food.opencook.data.remote.dto.SyncResponseDto
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Household + sync endpoints. The base URL is rewritten at runtime (see [BaseUrlInterceptor]). */
interface SyncApi {

    /** List households on this server for the join picker. */
    @GET("households")
    suspend fun listHouseholds(): List<HouseholdSummaryDto>

    @POST("households")
    suspend fun createHousehold(@Body body: CreateHouseholdRequest): HouseholdDto

    /** Join by household id (open ones need no PIN; protected ones do). */
    @POST("households/{id}/join")
    suspend fun joinHousehold(
        @Path("id") id: String,
        @Body body: JoinHouseholdRequest,
    ): HouseholdDto

    /** Edit household-wide settings (e.g. person count). Auth via the sync credential. */
    @PATCH("households/{id}")
    suspend fun patchHousehold(
        @Path("id") id: String,
        @Header("X-Household-Code") code: String,
        @Body body: PatchHouseholdRequest,
    ): HouseholdDto

    @POST("sync")
    suspend fun sync(
        @Header("X-Household-Code") code: String,
        @Body body: SyncRequestDto,
    ): SyncResponseDto

    /** Upload a locally-sourced image (raw JPEG bytes); returns its server filename
     *  to use as the recipe's imageRef so it syncs to other devices. */
    @POST("images")
    suspend fun uploadImage(
        @Header("X-Household-Code") code: String,
        @Body body: RequestBody,
    ): ImageUploadResponseDto

    /** Download an image by its server filename. Used to copy synced recipes' photos
     *  into local storage so they stay visible when the server is unreachable. */
    @GET("images/{name}")
    suspend fun downloadImage(@Path("name") name: String): ResponseBody
}
