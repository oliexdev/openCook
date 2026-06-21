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

import com.food.opencook.data.remote.dto.CreateJobResponseDto
import com.food.opencook.data.remote.dto.JobResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Recipe-extraction job endpoints. Crops are fetched separately by Coil (GET /images/{name}). */
interface JobsApi {

    /** The multipart field name MUST be "image" to match the server's UploadFile.
     *  ``language`` is the recipe content language (e.g. "de"/"en") for the prompt. */
    @Multipart
    @POST("jobs")
    suspend fun createJob(
        @Part image: MultipartBody.Part,
        @Part("language") language: RequestBody? = null,
    ): CreateJobResponseDto

    @GET("jobs/{id}")
    suspend fun getJob(@Path("id") id: String): JobResponseDto
}
