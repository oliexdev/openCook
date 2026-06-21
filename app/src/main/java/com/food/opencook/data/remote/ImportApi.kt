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
