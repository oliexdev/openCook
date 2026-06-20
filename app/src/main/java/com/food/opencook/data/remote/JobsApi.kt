package com.food.opencook.data.remote

import com.food.opencook.data.remote.dto.CreateJobResponseDto
import com.food.opencook.data.remote.dto.JobResponseDto
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Recipe-extraction job endpoints. Crops are fetched separately by Coil (GET /images/{name}). */
interface JobsApi {

    /** The multipart field name MUST be "image" to match the server's UploadFile. */
    @Multipart
    @POST("jobs")
    suspend fun createJob(@Part image: MultipartBody.Part): CreateJobResponseDto

    @GET("jobs/{id}")
    suspend fun getJob(@Path("id") id: String): JobResponseDto
}
