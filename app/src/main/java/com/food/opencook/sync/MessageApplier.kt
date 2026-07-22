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
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.MealDayEntity
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.entity.RecipeLikeEntity
import com.food.opencook.data.local.entity.ShoppingItemEntity
import com.food.opencook.data.remote.dto.SyncMessageDto
import javax.inject.Inject
import javax.inject.Singleton

/** Below this many incoming recipes, apply silently (icon spins); at or above, show a
 *  determinate progress bar with the recipe count. Keeps everyday syncs noise-free. */
private const val MIN_RECIPES_FOR_PROGRESS = 20

/** Comfortably below SQLite's default 999 bind-variable limit. */
private const val EXISTING_TS_CHUNK = 900

/**
 * Ingests remote messages into the local log and projects them back into the
 * materialised Room tables. Shared by the sync initiator ([SyncEngine], applying a
 * server's or peer's response) and the responder ([SyncResponder], applying what a
 * peer pushed to us) so both paths converge identically.
 *
 * Deliberately does NOT fire [SyncTrigger.requestSync] — ingesting remote messages
 * must not re-trigger a sync, or two foregrounded peers would ping-pong forever.
 * The log is idempotent (HLC-timestamp PK, IGNORE on conflict), so even a stray
 * echo is harmless.
 */
@Singleton
class MessageApplier @Inject constructor(
    private val messageDao: MessageDao,
    private val recipeDao: RecipeDao,
    private val shoppingDao: ShoppingDao,
    private val pantryDao: PantryDao,
    private val mealPlanDao: MealPlanDao,
    private val mealDayDao: MealDayDao,
    private val recipeLikeDao: RecipeLikeDao,
    private val syncClock: SyncClock,
    private val transactor: Transactor,
) {
    suspend fun apply(
        messages: List<SyncMessageDto>,
        onProgress: (recipes: Int, fraction: Float) -> Unit = { _, _ -> },
    ) {
        if (messages.isEmpty()) return
        // Drop everything the log already holds BEFORE opening the transaction. Peers
        // push their entire log each round; without this filter every round re-projected
        // the whole database inside one big transaction, blocking concurrent local
        // writes (visible as a frozen shopping list while a sync was running). The
        // steady-state round then applies nothing at all.
        val novel = filterNovel(messages)
        if (novel.isEmpty()) return
        applyNovel(novel, onProgress)
    }

    /** Chunked (SQLite bind-variable limit) lookup of which messages are new to us. */
    private suspend fun filterNovel(messages: List<SyncMessageDto>): List<SyncMessageDto> {
        val known = HashSet<String>(messages.size)
        messages.map { it.timestamp }.chunked(EXISTING_TS_CHUNK).forEach { chunk ->
            known += messageDao.existingTimestamps(chunk)
        }
        return messages.filter { it.timestamp !in known }
    }

    private suspend fun applyNovel(
        messages: List<SyncMessageDto>,
        onProgress: (recipes: Int, fraction: Float) -> Unit,
    ) {
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
                        mealTypes = str("mealTypes"),
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
