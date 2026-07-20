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

package com.food.opencook.data.backup

import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.recipeimport.RecipeImportParser
import com.food.opencook.data.remote.mapper.toMappedRecipe
import com.food.opencook.repository.MealPlanRepository
import com.food.opencook.repository.PantryRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.SaveResult
import com.food.opencook.repository.ShoppingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** What a restore actually did, for the closing summary. */
data class BackupImportResult(
    val recipesImported: Int = 0,
    /** Recipes skipped because a *different* recipe already has that name. */
    val recipesSkipped: Int = 0,
    val imagesRestored: Int = 0,
    val shopping: Int = 0,
    val pantry: Int = 0,
    val mealPlan: Int = 0,
)

/**
 * Restores a [BackupFormat] archive by replaying it through the ordinary repository
 * write paths — the same ones the UI uses. Three consequences, all deliberate:
 *
 *  - **Additive.** Nothing is deleted. A restore can only bring things back.
 *  - **Idempotent.** Row ids travel in the archive, so re-running upserts the same rows.
 *  - **Undeletes.** Writing through the repositories stamps fresh HLCs, which beat the
 *    tombstone of anything deleted since the backup — so a recipe you deleted by mistake
 *    comes back, and the restore syncs out to the rest of the household.
 *
 * The archive is read **twice**: photos must exist on disk before the recipes that point
 * at them are saved, and streaming both in one pass would mean buffering the whole file.
 */
@Singleton
class BackupImporter @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
    private val pantryRepository: PantryRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val recipeDao: RecipeDao,
    private val imageStore: ImageStore,
    private val json: Json,
) {

    /**
     * @param open reopens the archive; called twice (photos, then content).
     * @throws BackupRejectedException if the archive is unreadable or looks hostile.
     */
    suspend fun import(
        open: () -> InputStream,
        onProgress: (phase: BackupPhase, fraction: Float) -> Unit = { _, _ -> },
    ): BackupImportResult = withContext(Dispatchers.IO) {
        val reader = BackupReader(json)
        // Validates the format version and that this is a backup at all, before writing.
        val manifest = open().use { reader.readManifest(it) }

        // --- Pass 1: photos to disk ---
        val restored = HashMap<String, String>()
        var seen = 0
        val expected = manifest.counts.images.coerceAtLeast(1)
        open().use { input ->
            reader.forEachEntry(input) { name, stream ->
                if (name.startsWith(BackupFormat.IMAGES_DIR)) {
                    val recipeId = name.removePrefix(BackupFormat.IMAGES_DIR).removeSuffix(".jpg")
                    // Best-effort: a single unreadable photo must not abort the restore.
                    runCatching { imageStore.saveRestoredImage(stream) }
                        .onSuccess { restored[recipeId] = it }
                    seen++
                    onProgress(BackupPhase.IMAGES, seen.toFloat() / expected)
                }
            }
        }

        // --- Pass 2: recipes, then the lists ---
        var result = BackupImportResult(imagesRestored = restored.size)
        open().use { input ->
            reader.forEachEntry(input) { name, stream ->
                when (name) {
                    BackupFormat.RECIPES -> {
                        val (imported, skipped) = importRecipes(stream, restored, onProgress)
                        result = result.copy(recipesImported = imported, recipesSkipped = skipped)
                    }
                    BackupFormat.SHOPPING -> {
                        val items = json.decodeFromString(ShoppingBackup.serializer(), stream.readBytes().decodeToString())
                            .items.map { it.toEntity() }
                        shoppingRepository.importItems(items)
                        result = result.copy(shopping = items.size)
                    }
                    BackupFormat.PANTRY -> {
                        val items = json.decodeFromString(PantryBackup.serializer(), stream.readBytes().decodeToString())
                            .items.map { it.toEntity() }
                        pantryRepository.importItems(items)
                        result = result.copy(pantry = items.size)
                    }
                    BackupFormat.MEALPLAN -> {
                        val plan = json.decodeFromString(MealPlanBackup.serializer(), stream.readBytes().decodeToString())
                        // A plan row whose recipe never made it in would dangle.
                        val entries = plan.entries.map { it.toEntity() }
                            .filter { recipeDao.recipeExists(it.recipeId) }
                        val days = plan.days.map { it.toEntity() }
                        mealPlanRepository.importEntries(entries, days)
                        result = result.copy(mealPlan = entries.size)
                    }
                    else -> Unit
                }
            }
        }
        onProgress(BackupPhase.LISTS, 1f)
        result
    }

    private suspend fun importRecipes(
        stream: InputStream,
        restored: Map<String, String>,
        onProgress: (BackupPhase, Float) -> Unit,
    ): Pair<Int, Int> {
        val dtos = RecipeImportParser.parse(stream.readBytes().decodeToString(), json)
        var imported = 0
        var skipped = 0
        dtos.forEachIndexed { index, dto ->
            coroutineContext.ensureActive()
            // The `image` refs are archive paths, not server names — drop them before
            // mapping (the mapper would store them as remoteName) and attach the file
            // we just restored instead.
            val mapped = dto.copy(image = emptyList()).toMappedRecipe(
                sourcePhotoId = null,
                now = System.currentTimeMillis(),
            )
            val recipeId = mapped.recipe.id
            val images = imagesFor(recipeId, restored[recipeId])
            val outcome = recipeRepository.saveRecipe(
                mapped.recipe, mapped.ingredients, mapped.instructions, mapped.nutrition, images,
            )
            if (outcome == SaveResult.Saved) imported++ else skipped++
            onProgress(BackupPhase.RECIPES, (index + 1).toFloat() / dtos.size)
        }
        return imported to skipped
    }

    /**
     * Which image rows the recipe should end up with. A photo already on this device wins:
     * re-attaching the archived copy would drop a `remoteName` the household already
     * uploaded and trigger a pointless re-upload of identical bytes.
     */
    private suspend fun imagesFor(recipeId: String, restoredPath: String?): List<ImageEntity> {
        val existing = recipeDao.imagesForRecipe(recipeId)
        val haveUsableLocal = existing.any { img -> img.localPath?.let { File(it).exists() } == true }
        if (haveUsableLocal || restoredPath == null) return existing
        return listOf(
            ImageEntity(
                id = "restored-$recipeId",
                recipeId = recipeId,
                position = 0,
                remoteName = null,
                localPath = restoredPath,
                isPrimary = true,
            ),
        )
    }
}
