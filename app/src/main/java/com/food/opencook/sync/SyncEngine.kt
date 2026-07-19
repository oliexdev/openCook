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

package com.food.opencook.sync

import com.food.opencook.data.discovery.ServerDiscovery
import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.data.remote.BaseUrlInterceptor
import com.food.opencook.data.remote.SyncApi
import com.food.opencook.data.remote.dto.SyncMessageDto
import com.food.opencook.data.remote.dto.SyncRequestDto
import com.food.opencook.data.remote.dto.SyncResponseDto
import com.food.opencook.data.settings.SettingsRepository
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Same idea as the recipe-apply threshold — a single missing image doesn't deserve a banner. */
private const val MIN_IMAGES_FOR_PROGRESS = 5

private const val TAG = "OpenCookP2P"

/** Cache key for the (single) configured server in [SyncEngine.remoteMerkles]. */
private const val SERVER_KEY = "@server"

/** How many image GETs to run at once. Recipe photos are a few hundred KB each so
 *  serial downloads burn most of the time on round-trips; 4 parallel saturates a
 *  typical home LAN without overwhelming the desktop server. */
private const val IMAGE_DOWNLOAD_PARALLELISM = 4

/**
 * Drives one round of delta-sync: build the local Merkle, push local messages and
 * pull the ones we're missing, then project them back into the materialised Room
 * tables (via the shared [MessageApplier]).
 *
 * Targets are tried in order: the configured server first (it is the household's
 * authority and holds the AI/import features), then any peer phones discovered on
 * the LAN — so the family's lists still flow while the desktop server is off, and
 * serverless households (no URL configured) sync purely phone-to-phone. The log is
 * idempotent, so syncing with several targets over time converges by construction.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val syncApi: SyncApi,
    @param:Named("peer") private val peerSyncApi: SyncApi,
    @param:Named("peer") private val peerUrlInterceptor: BaseUrlInterceptor,
    private val settings: SettingsRepository,
    private val messageDao: MessageDao,
    private val recipeDao: RecipeDao,
    private val syncClock: SyncClock,
    private val serverDiscovery: ServerDiscovery,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    private val imageStore: ImageStore,
    private val applier: MessageApplier,
) {
    /** When the server just proved unreachable, skip re-probing it for this long and go
     *  straight to peers. Keeps the foreground 30-s sync cadence snappy while the desktop
     *  is off; the server is picked up again at most a minute after it comes back. */
    private val serverBackoffMs = 60_000L

    @Volatile
    private var lastServerFailureMs = 0L

    /**
     * Last known Merkle trie of each sync target (from its previous response), so we
     * push only the messages the target is likely missing instead of the whole log
     * every round. Staleness is safe in both directions: too old ⇒ we push a superset
     * (idempotent); too new (target lost data) ⇒ its next response carries the real
     * trie, refreshing the cache, and the following round heals the gap. In-memory
     * only — first contact after process start pushes the full log, like before.
     */
    private val remoteMerkles = ConcurrentHashMap<String, Merkle>()

    sealed interface Result {
        data class Ok(val pulled: Int, val via: SyncVia) : Result
        data object NoHousehold : Result
        data class Failed(val message: String) : Result

        /** The server returned 404: our household credential is unknown there
         *  (server DB reset/reinstalled). Distinct from [Failed] — retrying won't help. */
        data object UnknownHousehold : Result
    }

    /** Per-phase progress update: which step is running and how far it's got. */
    data class Progress(
        val phase: SyncStatus.Phase,
        val count: Int,
        val total: Int,
        val fraction: Float,
    )

    /**
     * @param onProgress fires during both the message-apply and image-download phases.
     *   Throttled (per-percent for apply, per-image for downloads) so the UI isn't
     *   flooded. Stays silent for small syncs where neither phase warrants a banner.
     */
    suspend fun sync(onProgress: (Progress) -> Unit = {}): Result {
        val code = settings.householdCodeOnce()?.takeIf { it.isNotBlank() } ?: return Result.NoHousehold
        val serverConfigured = !settings.serverUrlOnce().isNullOrBlank()

        val inServerBackoff = System.currentTimeMillis() - lastServerFailureMs < serverBackoffMs
        if (serverConfigured && !inServerBackoff) {
            // First try the stored address. A 404 means the server doesn't know this
            // household (reset/reinstalled) — surface that rather than retrying. If the
            // call merely failed (unreachable), the server may have moved (new DHCP IP) —
            // re-discover it on the LAN once and retry.
            var attempt = runCatching { exchange(syncApi, code, isPeer = false, SERVER_KEY, onProgress) }
            if (attempt.exceptionOrNull().isUnknownHousehold()) return Result.UnknownHousehold
            if (attempt.isFailure && rediscoverServer()) {
                attempt = runCatching { exchange(syncApi, code, isPeer = false, SERVER_KEY, onProgress) }
                if (attempt.exceptionOrNull().isUnknownHousehold()) return Result.UnknownHousehold
            }
            attempt.getOrNull()?.let { pulled ->
                lastServerFailureMs = 0L
                return Result.Ok(pulled, SyncVia.Server)
            }
            lastServerFailureMs = System.currentTimeMillis()
        }

        // Server unreachable (or serverless household): fall back to peer phones on the
        // LAN — but only when the household's P2P switch is on (off means: behave
        // exactly like the pre-P2P app, no discovery, no peer traffic). A peer
        // answering 404 simply doesn't know our household (someone else's openCook in
        // the same Wi-Fi) — skip it, never surface UnknownHousehold for peers.
        if (!settings.p2pEnabledOnce()) return Result.Failed("")
        for (peer in serverDiscovery.discoverPeers()) {
            peerUrlInterceptor.setBaseUrl(peer.baseUrl())
            // Keyed by service name, not address: the ephemeral port changes with every
            // foreground session, the advertised name stays stable.
            val attempt = runCatching { exchange(peerSyncApi, code, isPeer = true, peer.name, onProgress) }
            attempt.exceptionOrNull()?.let {
                Log.w(TAG, "peer sync with '${peer.name}' (${peer.baseUrl()}) failed", it)
            }
            val pulled = attempt.getOrNull() ?: continue
            return Result.Ok(pulled, SyncVia.Peer(peer.name))
        }

        // Empty message → the UI renders a localized generic "sync failed" (see SettingsViewModel).
        return Result.Failed("")
    }

    /**
     * One full exchange with one target: push local images (where allowed), push the
     * whole log + our Merkle, adopt the household meta from the response, apply the
     * missing messages, then pull image files. Throws on transport errors so the
     * caller can decide between retry, fallback and surfacing the failure.
     */
    private suspend fun exchange(
        api: SyncApi,
        code: String,
        isPeer: Boolean,
        targetKey: String,
        onProgress: (Progress) -> Unit,
    ): Int {
        // Push any device-local images (bundle imports, camera shots) first, so the
        // imageRef they emit travels in this same round. Best-effort: a failure here
        // must not abort the message sync below. To a peer this only happens in
        // serverless households — with a server configured it stays the image
        // authority, and marking an image "uploaded" after pushing it to a phone
        // would leave the server permanently without the bytes.
        if (!isPeer || settings.serverUrlOnce().isNullOrBlank()) {
            runCatching { uploadLocalImages(api, code) }
        }

        val local = messageDao.all()
        val localTrie = MerkleTrie.build(local.map { it.timestamp })
        // Push only what the target is missing according to its last known trie —
        // the full log travels just once per target and process, not every 30 s.
        val cached = remoteMerkles[targetKey]
        val pushCursor = cached?.let { MerkleTrie.diff(localTrie, it) }
        val toPush = when {
            cached == null -> local
            pushCursor == null -> emptyList()
            else -> local.filter { Hlc.parse(it.timestamp).millis >= pushCursor }
        }
        val request = SyncRequestDto(
            merkle = localTrie.toDto(),
            messages = toPush.map { SyncMessageDto(it.timestamp, it.dataset, it.rowId, it.column, it.value) },
        )
        val response = api.sync(code, request)
        response.merkle?.let { remoteMerkles[targetKey] = it.toMerkle() }

        // Adopt household-wide state (name + settings like person count) so all
        // devices converge on it without a separate poll.
        if (isPeer) adoptPeerMeta(response) else adoptServerMeta(response)

        applier.apply(response.messages) { recipes, fraction ->
            onProgress(Progress(SyncStatus.Phase.APPLY, recipes, recipes, fraction))
        }
        // Pull synced images down to local storage so they stay visible after the
        // target goes offline. Best-effort: any image we can't fetch right now
        // (a peer may not hold every file) retries on the next sync round.
        runCatching { downloadRemoteImages(api, onProgress) }
        return response.messages.size
    }

    /** The server materialises household meta authoritatively — adopt unconditionally. */
    private suspend fun adoptServerMeta(response: SyncResponseDto) {
        response.householdName?.let { settings.setHouseholdName(it) }
        response.householdSettings?.let {
            settings.setHouseholdSize(it.householdSize)
            settings.setContentLanguage(it.contentLanguage)
        }
    }

    /**
     * Between peers no copy is authoritative, so the meta travels with an HLC stamp
     * and the newest one wins everywhere — without the stamp gate, two phones holding
     * different copies would overwrite each other on alternating rounds forever.
     */
    private suspend fun adoptPeerMeta(response: SyncResponseDto) {
        val remoteHlc = response.householdHlc ?: return
        val localHlc = settings.householdMetaHlcOnce()
        if (localHlc != null && remoteHlc <= localHlc) return
        response.householdName?.let { settings.setHouseholdName(it) }
        response.householdSettings?.let {
            settings.setHouseholdSize(it.householdSize)
            settings.setContentLanguage(it.contentLanguage)
        }
        settings.setHouseholdPin(response.householdPin)
        settings.setHouseholdMetaHlc(remoteHlc)
    }

    /** A 404 from the sync endpoint means the server has no such household
     *  (its [resolve_household] raises 404 for an unknown invite code). */
    private fun Throwable?.isUnknownHousehold(): Boolean =
        this is HttpException && code() == 404

    /**
     * Re-find the server on the LAN and update the stored address if it changed.
     * Returns true only when a *different* address was applied (so a retry makes
     * sense). Cheap and bounded: gives up quickly when off-LAN or the server is down.
     */
    private suspend fun rediscoverServer(): Boolean {
        val current = settings.serverUrlOnce()
        val found = serverDiscovery.discoverFirst() ?: return false
        val newUrl = found.baseUrl()
        if (newUrl == current) return false
        settings.setServerUrl(newUrl)
        baseUrlInterceptor.setBaseUrl(newUrl)
        return true
    }

    /**
     * Upload device-local images (a recipe's primary photo from a bundle import) to the
     * sync target so other devices can fetch them via GET /images/{name} and they survive
     * a reinstall. Each upload sets the row's [remoteName] and emits an `imageRef` message
     * (freshly stamped, so it wins) — exactly the shape AI photo crops already sync in.
     * Per-image best-effort: one failure (unreadable file / target error) skips that
     * image and leaves it local-only for the next round.
     */
    private suspend fun uploadLocalImages(api: SyncApi, code: String) {
        val locals = recipeDao.localOnlyImages()
        if (locals.isEmpty()) return
        val now = System.currentTimeMillis()
        for (img in locals) {
            val file = img.localPath?.let(::File)?.takeIf { it.exists() } ?: continue
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            val name = runCatching {
                api.uploadImage(code, bytes.toRequestBody("image/jpeg".toMediaType()))
            }.getOrNull()?.name ?: continue
            recipeDao.setImageRemoteName(img.id, name)
            if (img.isPrimary) {
                messageDao.insert(
                    MessageEntity(
                        timestamp = syncClock.stamp().pack(),
                        dataset = SyncDatasets.RECIPES,
                        rowId = img.recipeId,
                        column = "imageRef",
                        value = Json.encodeToString(String.serializer(), name),
                        createdAt = now,
                    ),
                )
            }
        }
    }

    /**
     * Download images that arrived via sync (we know the content-addressed filename but
     * have no local copy yet) so they keep rendering when the target is unreachable.
     * Per-image best-effort — a failure leaves the row remote-only and the next sync
     * round retries. Runs after the message apply while the target is known reachable.
     *
     * Parallelised behind a [IMAGE_DOWNLOAD_PARALLELISM]-permit semaphore so a fresh
     * household (dozens of photos at once) feels minutes, not tens of minutes, on a
     * typical home LAN. Emits a [Progress] update after each finished file so the UI
     * can show "Bilder laden … 17/50" instead of pretending the sync is done.
     */
    private suspend fun downloadRemoteImages(api: SyncApi, onProgress: (Progress) -> Unit) = coroutineScope {
        val remotes = recipeDao.remoteOnlyImages()
        if (remotes.isEmpty()) return@coroutineScope
        val total = remotes.size
        val done = AtomicInteger(0)
        // Stay silent for tiny rounds — flashing a "1/2 Bilder" banner for every
        // text-only edit would feel noisier than helpful.
        val reportProgress = total >= MIN_IMAGES_FOR_PROGRESS
        if (reportProgress) onProgress(Progress(SyncStatus.Phase.IMAGES, 0, total, 0f))
        val gate = Semaphore(IMAGE_DOWNLOAD_PARALLELISM)
        remotes.map { img ->
            async {
                gate.withPermit {
                    val name = img.remoteName ?: return@withPermit
                    // A peer may have pushed the bytes to us already (POST /images) —
                    // reuse the file instead of downloading our own copy back.
                    val existing = imageStore.existingDownload(name)
                    val path = existing ?: run {
                        val bytes = runCatching { api.downloadImage(name).use { it.bytes() } }
                            .getOrNull() ?: return@withPermit
                        runCatching { imageStore.saveDownloadedImage(name, bytes) }
                            .getOrNull() ?: return@withPermit
                    }
                    recipeDao.setImageLocalPath(img.id, path)
                }
                if (reportProgress) {
                    val finished = done.incrementAndGet()
                    onProgress(Progress(SyncStatus.Phase.IMAGES, finished, total, finished / total.toFloat()))
                }
            }
        }.awaitAll()
    }
}
