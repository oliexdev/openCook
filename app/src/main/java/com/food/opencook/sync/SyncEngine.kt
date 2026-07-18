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

import com.food.opencook.data.local.Transactor
import com.food.opencook.data.local.dao.MealDayDao
import com.food.opencook.data.local.dao.MealPlanDao
import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.dao.RecipeLikeDao
import com.food.opencook.data.local.dao.ShoppingDao
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.MealDayEntity
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.data.local.entity.RecipeLikeEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.data.discovery.ServerDiscovery
import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.remote.BaseUrlInterceptor
import com.food.opencook.data.remote.SyncApi
import com.food.opencook.data.remote.dto.MerkleDto
import com.food.opencook.data.remote.dto.SyncMessageDto
import com.food.opencook.data.remote.dto.SyncRequestDto
import com.food.opencook.data.settings.SettingsRepository
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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private fun Merkle.toMerkleDto(): MerkleDto =
    MerkleDto(hash = unsignedHash(), children = children.entries.associate { it.key.toString() to it.value.toMerkleDto() })

/** Below this many incoming recipes, apply silently (icon spins); at or above, show a
 *  determinate progress bar with the recipe count. Keeps everyday syncs noise-free. */
private const val MIN_RECIPES_FOR_PROGRESS = 20

/** Same idea for image downloads — a single missing image doesn't deserve a banner. */
private const val MIN_IMAGES_FOR_PROGRESS = 5

/** How many image GETs to run at once. Recipe photos are a few hundred KB each so
 *  serial downloads burn most of the time on round-trips; 4 parallel saturates a
 *  typical home LAN without overwhelming the desktop server. */
private const val IMAGE_DOWNLOAD_PARALLELISM = 4

/**
 * Drives one round of delta-sync: build the local Merkle, push local messages and
 * pull the ones we're missing, then project them back into the materialised Room
 * tables. Materialisation rebuilds each touched row from its winning field
 * messages, so it converges regardless of arrival order and handles tombstones.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val syncApi: SyncApi,
    private val settings: SettingsRepository,
    private val messageDao: MessageDao,
    private val recipeDao: RecipeDao,
    private val shoppingDao: ShoppingDao,
    private val pantryDao: PantryDao,
    private val mealPlanDao: MealPlanDao,
    private val mealDayDao: MealDayDao,
    private val recipeLikeDao: RecipeLikeDao,
    private val syncClock: SyncClock,
    private val serverDiscovery: ServerDiscovery,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    private val transactor: Transactor,
    private val imageStore: ImageStore,
) {
    sealed interface Result {
        data class Ok(val pulled: Int) : Result
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

        // Push any device-local images (bundle imports) to the server first, so the
        // imageRef they emit travels in this same round. Best-effort: a failure here
        // (server down) must not abort the message sync below.
        runCatching { uploadLocalImages(code) }

        val local = messageDao.all()
        val request = SyncRequestDto(
            merkle = MerkleTrie.build(local.map { it.timestamp }).toMerkleDto(),
            messages = local.map { SyncMessageDto(it.timestamp, it.dataset, it.rowId, it.column, it.value) },
        )

        // First try the stored address. A 404 means the server doesn't know this
        // household (reset/reinstalled) — surface that rather than retrying. If the
        // call merely failed (unreachable), the server may have moved (new DHCP IP) —
        // re-discover it on the LAN once and retry.
        var attempt = runCatching { syncApi.sync(code, request) }
        if (attempt.exceptionOrNull().isUnknownHousehold()) return Result.UnknownHousehold
        if (attempt.isFailure && rediscoverServer()) {
            attempt = runCatching { syncApi.sync(code, request) }
            if (attempt.exceptionOrNull().isUnknownHousehold()) return Result.UnknownHousehold
        }
        // Empty message → the UI renders a localized generic "sync failed" (see SettingsViewModel).
        val response = attempt.getOrNull() ?: return Result.Failed("")

        // Adopt household-wide state (name + settings like person count) so all
        // devices converge on it without a separate poll.
        response.householdName?.let { settings.setHouseholdName(it) }
        response.householdSettings?.let {
            settings.setHouseholdSize(it.householdSize)
            settings.setContentLanguage(it.contentLanguage)
        }

        applyRemote(response.messages) { recipes, fraction ->
            onProgress(Progress(SyncStatus.Phase.APPLY, recipes, recipes, fraction))
        }
        // Pull synced images down to local storage so they stay visible after the
        // server goes offline (it's a desktop that's often off). Best-effort: any
        // image we can't fetch right now retries on the next sync round.
        runCatching { downloadRemoteImages(onProgress) }
        return Result.Ok(response.messages.size)
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
        val newUrl = "http://${found.host}:${found.port}"
        if (newUrl == current) return false
        settings.setServerUrl(newUrl)
        baseUrlInterceptor.setBaseUrl(newUrl)
        return true
    }

    /**
     * Upload device-local images (a recipe's primary photo from a bundle import) to the
     * server so other devices can fetch them via GET /images/{name} and they survive a
     * reinstall. Each upload sets the row's [remoteName] and emits an `imageRef` message
     * (freshly stamped, so it wins) — exactly the shape AI photo crops already sync in.
     * Per-image best-effort: one failure (unreadable file / server error) skips that
     * image and leaves it local-only for the next round.
     */
    private suspend fun uploadLocalImages(code: String) {
        val locals = recipeDao.localOnlyImages()
        if (locals.isEmpty()) return
        val now = System.currentTimeMillis()
        for (img in locals) {
            val file = img.localPath?.let(::File)?.takeIf { it.exists() } ?: continue
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            val name = runCatching {
                syncApi.uploadImage(code, bytes.toRequestBody("image/jpeg".toMediaType()))
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
     * Download images that arrived via sync (we know the server filename but have no
     * local copy yet) so they keep rendering when the server is unreachable. Per-image
     * best-effort — a failure leaves the row remote-only and the next sync round retries.
     * Runs after [applyRemote] while the server is known reachable.
     *
     * Parallelised behind a [IMAGE_DOWNLOAD_PARALLELISM]-permit semaphore so a fresh
     * household (dozens of photos at once) feels minutes, not tens of minutes, on a
     * typical home LAN. Emits a [Progress] update after each finished file so the UI
     * can show "Bilder laden … 17/50" instead of pretending the sync is done.
     */
    private suspend fun downloadRemoteImages(onProgress: (Progress) -> Unit) = coroutineScope {
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
                    val bytes = runCatching { syncApi.downloadImage(name).use { it.bytes() } }
                        .getOrNull() ?: return@withPermit
                    val path = runCatching { imageStore.saveDownloadedImage(name, bytes) }
                        .getOrNull() ?: return@withPermit
                    recipeDao.setImageLocalPath(img.id, path)
                }
                if (reportProgress) {
                    val finished = done.incrementAndGet()
                    onProgress(Progress(SyncStatus.Phase.IMAGES, finished, total, finished / total.toFloat()))
                }
            }
        }.awaitAll()
    }

    private suspend fun applyRemote(
        messages: List<SyncMessageDto>,
        onProgress: (recipes: Int, fraction: Float) -> Unit,
    ) {
        if (messages.isEmpty()) return
        // One transaction for the whole apply: ~30k individual auto-commit writes collapse
        // into a single commit — far faster and atomic (no partial state if it aborts). The
        // onProgress callback only updates a StateFlow (not the DB), so the bar still
        // animates live; Room's recipe Flow emits once on commit (recipes appear together).
        transactor.withTransaction {
            val now = System.currentTimeMillis()
            val sorted = messages.sortedBy { it.timestamp }
            // Bulk-insert in a single statement (vs ~25k individual DAO calls crossing the
            // coroutine/JNI boundary), idempotent via the DAO's IGNORE-on-conflict.
            messageDao.insertAll(
                sorted.map { MessageEntity(it.timestamp, it.dataset, it.rowId, it.column, it.value, createdAt = now) },
            )
            // Note the touched rows (in-memory, cheap) so projection knows what to rebuild,
            // then advance our clock ONCE past the newest remote stamp. Observing every
            // message persisted the clock to settings ~25k times (~60s on a real device);
            // the packed HLC sorts lexicographically, so the last sorted entry is the
            // maximum — observing just it keeps the local clock past every remote stamp.
            val touched = LinkedHashSet<Pair<String, String>>()
            sorted.forEach { touched += it.dataset to it.rowId }
            sorted.lastOrNull()?.let { syncClock.observe(Hlc.parse(it.timestamp)) }
            // Project parents before children so foreign keys resolve.
            val recipeRows = touched.filter { it.first == SyncDatasets.RECIPES }
            val otherRows = touched.filter { it.first != SyncDatasets.RECIPES }

            // Surface a determinate bar only for large pulls (an initial household sync);
            // small syncs just spin the icon. The fraction spans *all* projected rows so it
            // tracks real work — most of which is the ingredient/instruction tail — while the
            // recipe count (projected first) climbs early and then caps. Throttled to one
            // emission per whole percent so a 1000-recipe pull updates ~100×, not thousands.
            val total = touched.size
            val report = recipeRows.size >= MIN_RECIPES_FOR_PROGRESS
            var applied = 0
            var lastPct = -1
            fun tick() {
                applied++
                if (!report) return
                val pct = applied * 100 / total
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(minOf(applied, recipeRows.size), applied.toFloat() / total)
                }
            }
            recipeRows.forEach { project(it.first, it.second); tick() }
            otherRows.forEach { project(it.first, it.second); tick() }
        }
    }

    /** Rebuild one materialised row from the winning (max-HLC) value of each field. */
    private suspend fun project(dataset: String, rowId: String) {
        val winning = messageDao.forRow(dataset, rowId)
            .groupBy { it.column }
            .mapValues { (_, msgs) -> msgs.maxBy { it.timestamp }.value }
        if (winning.isEmpty()) return

        fun str(col: String) = MessageCodec.decodeString(winning[col])

        when (dataset) {
            SyncDatasets.RECIPES -> {
                if (MessageCodec.isTrue(winning[SyncDatasets.COLUMN_DELETED])) {
                    recipeDao.deleteRecipe(rowId) // tombstone wins; row + children removed
                    return
                }
                val now = System.currentTimeMillis()
                recipeDao.upsertRecipeEntity(
                    RecipeEntity(
                        id = rowId,
                        name = str("name"),
                        description = str("description"),
                        recipeYield = str("recipeYield"),
                        prepTime = str("prepTime"),
                        cookTime = str("cookTime"),
                        totalTime = str("totalTime"),
                        notes = str("notes"),
                        tags = str("tags"),
                        lastCookedAt = str("lastCookedAt"),
                        cookbook = str("cookbook"),
                        servings = MessageCodec.decodeNullableInt(winning["servings"]),
                        category = str("category"),
                        // Link back to the server job whose original photo this recipe
                        // came from (kept so it can be re-extracted with a better model).
                        sourcePhotoId = str("sourcePhotoId"),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                str("imageRef")?.let { remoteName ->
                    // Collapse the recipe to a single canonical image row keyed by the
                    // recipe id. Local creation/edit paths key images with random UUIDs,
                    // so without this a remote imageRef change would spawn a *second*
                    // isPrimary row and leave the device's own (stale) one behind — the
                    // unordered @Relation could then keep showing the pre-edit image
                    // (e.g. a crop made on another device never appearing here).
                    //
                    // Preserve a localPath only from a row whose remoteName already matches
                    // (a text-only edit re-emits the same imageRef, and the editing device
                    // keeps its just-uploaded local file) — otherwise wipe it so
                    // downloadRemoteImages fetches the new crop.
                    val existing = recipeDao.imagesForRecipe(rowId)
                    val keepLocal = existing.firstOrNull { it.remoteName == remoteName }?.localPath
                    recipeDao.deleteImagesForRecipe(rowId)
                    recipeDao.upsertImageRow(
                        ImageEntity("sync-$rowId", rowId, 0, remoteName = remoteName, localPath = keepLocal, isPrimary = true),
                    )
                }
            }
            SyncDatasets.INGREDIENTS -> {
                if (MessageCodec.isTrue(winning[SyncDatasets.COLUMN_DELETED])) {
                    recipeDao.deleteIngredientById(rowId)
                    return
                }
                val recipeId = str("recipeId") ?: return
                if (!recipeDao.recipeExists(recipeId)) return
                recipeDao.upsertIngredientRow(
                    IngredientEntity(
                        id = rowId,
                        recipeId = recipeId,
                        position = MessageCodec.decodeInt(winning["position"]),
                        quantity = MessageCodec.decodeNullableDouble(winning["quantity"]),
                        unit = str("unit"),
                        name = str("name") ?: "",
                    ),
                )
            }
            SyncDatasets.INSTRUCTIONS -> {
                if (MessageCodec.isTrue(winning[SyncDatasets.COLUMN_DELETED])) {
                    recipeDao.deleteInstructionById(rowId)
                    return
                }
                val recipeId = str("recipeId") ?: return
                if (!recipeDao.recipeExists(recipeId)) return
                recipeDao.upsertInstructionRow(
                    InstructionEntity(rowId, recipeId, MessageCodec.decodeInt(winning["position"]), str("text") ?: ""),
                )
            }
            SyncDatasets.NUTRITION -> {
                if (!recipeDao.recipeExists(rowId)) return
                recipeDao.upsertNutritionRow(
                    NutritionEntity(
                        recipeId = rowId,
                        calories = str("calories"),
                        proteinContent = str("proteinContent"),
                        fatContent = str("fatContent"),
                        carbohydrateContent = str("carbohydrateContent"),
                        fiberContent = str("fiberContent"),
                        sugarContent = str("sugarContent"),
                        basis = str("basis"),
                    ),
                )
            }
            SyncDatasets.SHOPPING -> {
                if (MessageCodec.isTrue(winning[SyncDatasets.COLUMN_DELETED])) {
                    shoppingDao.deleteById(rowId)
                    return
                }
                val now = System.currentTimeMillis()
                shoppingDao.upsert(
                    ShoppingItemEntity(
                        id = rowId,
                        text = str("text") ?: "",
                        quantity = MessageCodec.decodeNullableDouble(winning["quantity"]),
                        unit = str("unit"),
                        checked = MessageCodec.isTrue(winning["checked"]),
                        position = MessageCodec.decodeInt(winning["position"]),
                        sourceRecipeId = str("sourceRecipeId"),
                        sourceDate = str("sourceDate"),
                        manual = MessageCodec.isTrue(winning["manual"]),
                        sourceRecipeIds = str("sourceRecipeIds"),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            SyncDatasets.PANTRY -> {
                if (MessageCodec.isTrue(winning[SyncDatasets.COLUMN_DELETED])) {
                    pantryDao.deleteById(rowId)
                    return
                }
                val now = System.currentTimeMillis()
                pantryDao.upsert(PantryItemEntity(id = rowId, name = str("name") ?: "", createdAt = now, updatedAt = now))
            }
            SyncDatasets.MEALPLAN -> {
                if (MessageCodec.isTrue(winning[SyncDatasets.COLUMN_DELETED])) {
                    mealPlanDao.deleteById(rowId)
                    return
                }
                val date = str("date") ?: return
                val recipeId = str("recipeId") ?: return
                val now = System.currentTimeMillis()
                mealPlanDao.upsert(
                    MealPlanEntity(
                        id = rowId,
                        date = date,
                        recipeId = recipeId,
                        pinned = MessageCodec.isTrue(winning["pinned"]),
                        // Field is optional and may be absent on entries written by an
                        // older app version — treat as "no reasons" rather than failing.
                        reasonsJson = str("reasonsJson"),
                        // Optional/absent-tolerant like reasonsJson — older apps never sent it.
                        cookedAt = str("cookedAt"),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            SyncDatasets.MEAL_DAYS -> {
                if (MessageCodec.isTrue(winning[SyncDatasets.COLUMN_DELETED])) {
                    mealDayDao.deleteByDate(rowId)
                    return
                }
                val now = System.currentTimeMillis()
                mealDayDao.upsert(
                    MealDayEntity(
                        date = rowId,
                        skipped = MessageCodec.isTrue(winning["skipped"]),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            SyncDatasets.RECIPE_LIKES -> {
                // recipeId/nodeId come from fields (rowId is "recipeId:nodeId" and
                // recipe ids may themselves contain no ':' — but reading the fields is robust).
                val recipeId = str("recipeId") ?: return
                val nodeId = str("nodeId") ?: return
                val now = System.currentTimeMillis()
                recipeLikeDao.upsert(
                    RecipeLikeEntity(
                        recipeId = recipeId,
                        nodeId = nodeId,
                        liked = MessageCodec.isTrue(winning["liked"]),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}
