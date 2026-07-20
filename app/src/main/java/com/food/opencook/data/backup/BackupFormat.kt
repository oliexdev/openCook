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

import kotlinx.serialization.Serializable

/**
 * On-device backup archive — a plain `.zip` whose parts are all human-readable:
 *
 * ```
 * manifest.json     metadata for the restore dialog
 * recipes.json      schema.org/Recipe array (the interchange standard)
 * shopping.json     openCook JSON (no standard exists for a shopping list)
 * pantry.json
 * mealplan.json
 * images/<recipeId>.jpg
 * ```
 *
 * Deliberately **not** in the archive:
 *  - the household's invite code, id and PIN — a backup is pure content, carries no
 *    secret, and can be restored into *any* household (including a friend's);
 *  - the CRDT message log — restoring replays through the normal repository write path
 *    instead, so a restore is always additive, idempotent and never destructive;
 *  - recipe likes (device-scoped planner signals), the barcode product cache and jobs.
 */
object BackupFormat {
    /** Bumped only on a breaking change; a reader refuses anything higher. */
    const val VERSION = 1

    const val MANIFEST = "manifest.json"
    const val RECIPES = "recipes.json"
    const val SHOPPING = "shopping.json"
    const val PANTRY = "pantry.json"
    const val MEALPLAN = "mealplan.json"
    const val IMAGES_DIR = "images/"

    const val MIME = "application/zip"

    /** Guards against a hostile archive: a single entry and the whole archive are capped
     *  well above any plausible real backup but far below a zip bomb's expansion. */
    const val MAX_ENTRY_BYTES = 50L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

    private val IMAGE_ENTRY = Regex("""^images/[A-Za-z0-9._-]{1,80}\.jpg$""")
    private val JSON_ENTRY = Regex("""^(manifest|recipes|shopping|pantry|mealplan)\.json$""")

    /**
     * Whether [name] is an entry we are willing to read. Rejects path traversal
     * (`..`, absolute paths, backslashes) and anything outside the known layout —
     * the archive arrives from outside the app, and [com.food.opencook.data.image.ImageStore]
     * does not validate the names it is handed.
     */
    fun isAllowedEntry(name: String): Boolean {
        if (name.contains("..") || name.contains('\\') || name.startsWith("/")) return false
        return JSON_ENTRY.matches(name) || IMAGE_ENTRY.matches(name)
    }

    /** Archive-relative path of a recipe's photo. */
    fun imageEntry(recipeId: String) = "$IMAGES_DIR$recipeId.jpg"
}

@Serializable
data class BackupManifest(
    val formatVersion: Int = BackupFormat.VERSION,
    /** ISO-8601 UTC instant the archive was written. */
    val createdAt: String = "",
    val appVersionName: String? = null,
    /** Display label only, so the restore dialog can say where the backup came from.
     *  Never an id, invite code or PIN. */
    val householdName: String? = null,
    val counts: BackupCounts = BackupCounts(),
)

@Serializable
data class BackupCounts(
    val recipes: Int = 0,
    val images: Int = 0,
    /** Photos whose file could not be read at export time (counted, not fatal). */
    val imagesMissing: Int = 0,
    val shopping: Int = 0,
    val pantry: Int = 0,
    val mealPlan: Int = 0,
    val mealDays: Int = 0,
)

// --- The lists. schema.org has no meaningful vocabulary for these, so rather than
// dress them up as an ItemList nobody interoperates with, they are plain openCook JSON
// that mirrors the Room rows one-to-one (ids included, so a restore upserts in place). ---

@Serializable
data class ShoppingBackup(
    val formatVersion: Int = BackupFormat.VERSION,
    val items: List<ShoppingItemBackup> = emptyList(),
)

@Serializable
data class ShoppingItemBackup(
    val id: String,
    val text: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val checked: Boolean = false,
    val position: Int = 0,
    val sourceRecipeId: String? = null,
    val sourceDate: String? = null,
    val manual: Boolean = false,
    val sourceRecipeIds: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class PantryBackup(
    val formatVersion: Int = BackupFormat.VERSION,
    val items: List<PantryItemBackup> = emptyList(),
)

@Serializable
data class PantryItemBackup(
    val id: String,
    val name: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class MealPlanBackup(
    val formatVersion: Int = BackupFormat.VERSION,
    val entries: List<MealPlanEntryBackup> = emptyList(),
    val days: List<MealDayBackup> = emptyList(),
)

@Serializable
data class MealPlanEntryBackup(
    val id: String,
    val date: String,
    val recipeId: String,
    val pinned: Boolean = false,
    val reasonsJson: String? = null,
    val cookedAt: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class MealDayBackup(
    val date: String,
    val skipped: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
