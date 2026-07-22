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

import com.food.opencook.BuildConfig
import com.food.opencook.data.local.dao.GroceryOverrideDao
import com.food.opencook.data.local.dao.MealDayDao
import com.food.opencook.data.local.dao.MealPlanDao
import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.dao.ShoppingDao
import com.food.opencook.data.local.relation.RecipeWithDetails
import com.food.opencook.data.remote.dto.RecipeDto
import com.food.opencook.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Where the export currently is, so the UI can show something honest. */
enum class BackupPhase { RECIPES, IMAGES, LISTS }

/**
 * Writes a [BackupFormat] archive straight into an already-open [OutputStream] (a
 * Storage Access Framework document, or a `ByteArrayOutputStream` in tests).
 *
 * Everything streams. A library of a few hundred recipes with photos is easily
 * several hundred megabytes, so recipes are paged out of the database and image files
 * are copied through rather than read into memory.
 */
@Singleton
class BackupWriter @Inject constructor(
    private val recipeDao: RecipeDao,
    private val shoppingDao: ShoppingDao,
    private val pantryDao: PantryDao,
    private val mealPlanDao: MealPlanDao,
    private val mealDayDao: MealDayDao,
    private val groceryOverrideDao: GroceryOverrideDao,
    private val settings: SettingsRepository,
    private val json: Json,
) {

    suspend fun write(
        out: OutputStream,
        onProgress: (phase: BackupPhase, fraction: Float) -> Unit = { _, _ -> },
    ): BackupManifest = withContext(Dispatchers.IO) {
        val total = recipeDao.recipeCount()
        // Collected while streaming recipes, then written with the lists at the end.
        val imageFiles = LinkedHashMap<String, File>()
        var missingImages = 0
        var written = 0

        ZipOutputStream(out.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)

            // --- recipes.json, paged so a huge library never lands in memory at once ---
            zip.putNextEntry(ZipEntry(BackupFormat.RECIPES))
            zip.write("[".toByteArray())
            var offset = 0
            while (offset < total) {
                coroutineContext.ensureActive()
                val page = recipeDao.pageWithDetails(PAGE_SIZE, offset)
                if (page.isEmpty()) break
                for (details in page) {
                    val file = primaryImageFile(details)
                    val ref = if (file != null) {
                        imageFiles[details.recipe.id] = file
                        BackupFormat.imageEntry(details.recipe.id)
                    } else {
                        if (details.images.isNotEmpty()) missingImages++
                        null
                    }
                    if (written > 0) zip.write(",".toByteArray())
                    zip.write(json.encodeToString(RecipeDto.serializer(), RecipeDtoEncoder.encode(details, ref)).toByteArray())
                    written++
                }
                offset += page.size
                onProgress(BackupPhase.RECIPES, if (total == 0) 1f else written.toFloat() / total)
            }
            zip.write("]".toByteArray())
            zip.closeEntry()

            // --- images/ — stored, not deflated: JPEG does not compress and trying
            // costs real CPU on a phone for a few hundred megabytes. ---
            zip.setLevel(Deflater.NO_COMPRESSION)
            var storedImages = 0
            var seen = 0
            for ((recipeId, file) in imageFiles) {
                coroutineContext.ensureActive()
                // Best-effort per image, exactly like SyncEngine's upload loop: one
                // unreadable file must not lose the other 400 recipes.
                runCatching {
                    zip.putNextEntry(ZipEntry(BackupFormat.imageEntry(recipeId)))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }.onSuccess { storedImages++ }.onFailure { missingImages++ }
                seen++
                onProgress(BackupPhase.IMAGES, seen.toFloat() / imageFiles.size)
            }
            zip.setLevel(Deflater.BEST_SPEED)

            // --- the lists (small; no need to page) ---
            onProgress(BackupPhase.LISTS, 0f)
            val shopping = shoppingDao.getAll()
            zip.entry(BackupFormat.SHOPPING) {
                json.encodeToString(
                    ShoppingBackup.serializer(),
                    ShoppingBackup(items = shopping.map { it.toBackup() }),
                )
            }
            val pantry = pantryDao.getAll()
            zip.entry(BackupFormat.PANTRY) {
                json.encodeToString(
                    PantryBackup.serializer(),
                    PantryBackup(items = pantry.map { it.toBackup() }),
                )
            }
            val planEntries = mealPlanDao.getAll()
            val planDays = mealDayDao.getAll()
            zip.entry(BackupFormat.MEALPLAN) {
                json.encodeToString(
                    MealPlanBackup.serializer(),
                    MealPlanBackup(
                        entries = planEntries.map { it.toBackup() },
                        days = planDays.map { it.toBackup() },
                    ),
                )
            }
            val overrides = groceryOverrideDao.getAll()
            zip.entry(BackupFormat.GROCERY_OVERRIDES) {
                json.encodeToString(
                    GroceryOverridesBackup.serializer(),
                    GroceryOverridesBackup(items = overrides.map { it.toBackup() }),
                )
            }

            val manifest = BackupManifest(
                createdAt = isoNow(),
                appVersionName = BuildConfig.VERSION_NAME,
                householdName = settings.householdNameOnce(),
                counts = BackupCounts(
                    recipes = written,
                    images = storedImages,
                    imagesMissing = missingImages,
                    shopping = shopping.size,
                    pantry = pantry.size,
                    mealPlan = planEntries.size,
                    mealDays = planDays.size,
                    groceryOverrides = overrides.size,
                ),
            )
            zip.entry(BackupFormat.MANIFEST) { json.encodeToString(BackupManifest.serializer(), manifest) }
            onProgress(BackupPhase.LISTS, 1f)
            manifest
        }
    }

    /** The recipe's own photo file, if one exists on this device. Prefers the primary
     *  row; a recipe synced in but whose bytes were never downloaded has none. */
    private fun primaryImageFile(details: RecipeWithDetails): File? =
        details.images
            .sortedByDescending { it.isPrimary }
            .firstNotNullOfOrNull { img ->
                img.localPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
            }

    private inline fun ZipOutputStream.entry(name: String, body: () -> String) {
        putNextEntry(ZipEntry(name))
        write(body().toByteArray())
        closeEntry()
    }

    private companion object {
        const val PAGE_SIZE = 50

        fun isoNow(): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
    }
}

/** Suggested file name; the timestamp sorts chronologically, which the rotation relies on. */
fun backupFileName(now: Date = Date()): String =
    "opencook-backup-" +
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) +
        ".zip"
