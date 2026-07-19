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

package com.food.opencook.data.peer

import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.remote.dto.HouseholdDto
import com.food.opencook.data.remote.dto.HouseholdSettings
import com.food.opencook.data.remote.dto.HouseholdSummaryDto
import com.food.opencook.data.remote.dto.ImageUploadResponseDto
import com.food.opencook.data.remote.dto.JoinHouseholdRequest
import com.food.opencook.data.remote.dto.SyncRequestDto
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.sync.SyncResponder
import android.util.Log
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone-side twin of the Python server's sync surface: while the app is in the
 * foreground (see PeerAdvertiser), an embedded Ktor server answers the exact same
 * wire contract — POST /sync, the image blob endpoints and the household join flow —
 * so another phone's completely unchanged client code can sync against this device.
 *
 * Trust model, deliberately stricter than the Python server: every endpoint except
 * the join flow requires the household invite code (`X-Household-Code`), including
 * image downloads — this listener may come up in a foreign Wi-Fi, where a stranger
 * must learn nothing beyond the household's existence. An unknown code answers 404
 * exactly like the server's resolve_household, which the initiating side already
 * understands (it skips this peer).
 */
@Singleton
class PeerSyncServer @Inject constructor(
    private val responder: SyncResponder,
    private val settings: SettingsRepository,
    private val imageStore: ImageStore,
    private val recipeDao: RecipeDao,
    private val json: Json,
) {
    private var server: EmbeddedServer<*, *>? = null

    /** Start listening on an ephemeral port; returns the bound port, or null when the
     *  server could not start (best-effort — P2P then simply isn't offered). Callers run
     *  off the main thread (PeerAdvertiser's IO scope) — deliberately no withContext
     *  wrapper here: a nested dispatch boundary at this spot never resumed in release
     *  builds (bring-up froze right after the bind), so the call chain stays linear. */
    suspend fun start(): Int? {
        server?.let { return runCatching { it.port() }.getOrNull() }
        return runCatching {
            val s = embeddedServer(CIO, port = 0, host = "0.0.0.0") { module() }
            s.start(wait = false)
            server = s
            s.port().also { Log.d(TAG, "peer server listening on port $it") }
        }.onFailure { Log.w(TAG, "peer server failed to start", it) }.getOrNull()
    }

    fun stop() {
        server?.let { runCatching { it.stop(gracePeriodMillis = 250, timeoutMillis = 1_000) } }
        server = null
    }

    private suspend fun EmbeddedServer<*, *>.port(): Int =
        engine.resolvedConnectors().first().port

    /** The invite code is the sync credential — exactly the server's trust model. */
    private suspend fun ApplicationCall.authorized(): Boolean {
        val own = settings.householdCodeOnce()
        val presented = request.headers["X-Household-Code"]
        if (own.isNullOrBlank() || presented != own) {
            respond(HttpStatusCode.NotFound) // mirror resolve_household: unknown household
            return false
        }
        return true
    }

    private suspend fun householdSettings() = HouseholdSettings(
        householdSize = settings.householdSizeOnce(),
        contentLanguage = settings.contentLanguageOnce(),
    )

    private fun Application.module() {
        install(ContentNegotiation) { json(this@PeerSyncServer.json) }
        routing {
            // Join flow: same shape as the server's households API, but a phone hosts
            // exactly one household — its own. Listed without the invite code; the
            // code is handed out only by a successful (PIN-checked) join.
            get("/households") {
                val id = settings.householdIdOnce()
                val name = settings.householdNameOnce()
                if (id == null || name == null) {
                    call.respond(emptyList<HouseholdSummaryDto>())
                } else {
                    call.respond(
                        listOf(
                            HouseholdSummaryDto(
                                id = id,
                                name = name,
                                settings = householdSettings(),
                                protected = settings.householdPinOnce() != null,
                            ),
                        ),
                    )
                }
            }
            post("/households/{id}/join") {
                val ownId = settings.householdIdOnce()
                val code = settings.householdCodeOnce()
                if (ownId == null || code == null || call.parameters["id"] != ownId) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }
                val body = call.receive<JoinHouseholdRequest>()
                val pin = settings.householdPinOnce()
                if (pin != null && body.pin != pin) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                call.respond(
                    HouseholdDto(
                        householdId = ownId,
                        inviteCode = code,
                        name = settings.householdNameOnce() ?: "",
                        settings = householdSettings(),
                    ),
                )
            }

            post("/sync") {
                if (!call.authorized()) return@post
                call.respond(responder.respond(call.receive<SyncRequestDto>()))
            }

            // Image blobs, content-addressed by sha256 exactly like the Python server,
            // so the same bytes get the same name no matter which member stores them.
            post("/images") {
                if (!call.authorized()) return@post
                val bytes = call.receive<ByteArray>()
                val name = sha256Hex(bytes) + ".jpg"
                imageStore.saveDownloadedImage(name, bytes)
                call.respond(ImageUploadResponseDto(name))
            }
            get("/images/{name}") {
                if (!call.authorized()) return@get
                val name = call.parameters["name"] ?: run {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                // Serve whatever copy this device holds: an own photo already uploaded
                // under this name, or a file previously downloaded/pushed via sync.
                // Both lookups resolve through sanitised, device-owned paths (the DAO
                // stores absolute paths we wrote ourselves; existingDownload guards
                // against traversal), never by joining [name] onto a directory here.
                val path = recipeDao.localPathForRemoteName(name)
                    ?: imageStore.existingDownload(name)
                val file = path?.let(::File)?.takeIf { it.isFile }
                if (file == null) call.respond(HttpStatusCode.NotFound) else call.respondFile(file)
            }
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private const val TAG = "OpenCookP2P"
