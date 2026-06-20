package com.food.opencook.data.remote

import com.food.opencook.data.remote.dto.AppReleaseDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Self-hosted app update endpoints. Base URL is rewritten at runtime (see [BaseUrlInterceptor]). */
interface UpdateApi {

    /** Latest published release metadata, or a 404 when nothing is published yet. */
    @GET("app/latest")
    suspend fun latest(): AppReleaseDto

    /** Stream the APK bytes. [url] is the server-relative path from [AppReleaseDto.url]. */
    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody
}
