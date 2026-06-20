package com.food.opencook.data.remote

import com.food.opencook.data.remote.dto.AdminPasswordChangeDto
import com.food.opencook.data.remote.dto.AdminStatusDto
import com.food.opencook.data.remote.dto.BackupInfoDto
import com.food.opencook.data.remote.dto.BackupListDto
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.data.remote.dto.RestoreRequestDto
import com.food.opencook.data.remote.dto.RestoreResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Password-gated server administration (backup/restore). Uses the same Retrofit
 * instance as [SyncApi], so the base URL is rewritten by [BaseUrlInterceptor].
 * The admin password is sent per request in the X-Admin-Password header; it is
 * never persisted on the device.
 */
interface AdminApi {

    /** Whether an admin password is configured yet (open — no auth). */
    @GET("admin/status")
    suspend fun status(): AdminStatusDto

    /** Set (when unset) or change the admin password. */
    @POST("admin/password")
    suspend fun setPassword(@Body body: AdminPasswordChangeDto): Response<Unit>

    /** Validate the password (204) before unlocking the admin screen. */
    @POST("admin/verify")
    suspend fun verify(@Header("X-Admin-Password") password: String): Response<Unit>

    @GET("admin/backups")
    suspend fun listBackups(@Header("X-Admin-Password") password: String): BackupListDto

    @POST("admin/backups")
    suspend fun createBackup(@Header("X-Admin-Password") password: String): BackupInfoDto

    @POST("admin/restore")
    suspend fun restore(
        @Header("X-Admin-Password") password: String,
        @Body body: RestoreRequestDto,
    ): RestoreResultDto

    /** Wipe all server data (keeps backups + admin password). For testing/reset. */
    @POST("admin/reset")
    suspend fun reset(@Header("X-Admin-Password") password: String): Response<Unit>

    /** List all households on the server (open endpoint; reused for the admin list). */
    @GET("households")
    suspend fun households(): List<HouseholdSummaryDto>

    /** Delete one household + its sync log and pending imports. */
    @DELETE("admin/households/{id}")
    suspend fun deleteHousehold(
        @Path("id") id: String,
        @Header("X-Admin-Password") password: String,
    ): Response<Unit>
}
