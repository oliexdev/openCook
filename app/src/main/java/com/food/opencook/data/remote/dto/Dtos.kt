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

package com.food.opencook.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire DTOs mirroring exactly what the server emits (see server/app/extraction.py
 * to_schema_org + server/app/schemas.py). Parsed with a tolerant Json
 * (ignoreUnknownKeys, explicitNulls=false) so server additions never break the app.
 */

/** Wire message — matches the server's SyncMessageDto. */
@Serializable
data class SyncMessageDto(
    val timestamp: String,
    val dataset: String,
    @SerialName("row_id") val rowId: String,
    val column: String,
    val value: String,
)

/** One pending recipe import from the inbox (browser-extension scrape). [recipe] is the
 *  raw schema.org/Recipe object — fed verbatim to [RecipeImportParser]. */
@Serializable
data class PendingImportDto(
    val id: String,
    val recipe: JsonElement,
    @SerialName("image_name") val imageName: String? = null,
    // Original page URL; used to group the import under its source site as a cookbook.
    // Tolerant of older servers that don't send it (→ null).
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
data class PendingImportsResponseDto(
    val imports: List<PendingImportDto> = emptyList(),
)

/** Serialised Merkle node: unsigned 32-bit hash + base-3 digit children. */
@Serializable
data class MerkleDto(
    val hash: Long = 0,
    val children: Map<String, MerkleDto> = emptyMap(),
)

@Serializable
data class SyncRequestDto(
    val merkle: MerkleDto,
    val messages: List<SyncMessageDto>,
)

@Serializable
data class SyncResponseDto(
    val messages: List<SyncMessageDto> = emptyList(),
    val merkle: MerkleDto? = null,
    /** Household-wide state pushed with every sync so all devices converge. */
    @SerialName("household_name") val householdName: String? = null,
    @SerialName("household_settings") val householdSettings: HouseholdSettings? = null,
)

/**
 * Extensible household-wide settings (mirror of the server's HouseholdSettings).
 * Tolerant Json drops unknown keys; the server keeps them, so a future option can
 * be added here later without breaking older clients.
 */
@Serializable
data class HouseholdSettings(
    @SerialName("household_size") val householdSize: Int = 2,
    /** ISO language code (e.g. "de"/"en") for recipe CONTENT: AI extraction, categories,
     *  grocery keywords, staples. null = each device follows its own system language. */
    @SerialName("content_language") val contentLanguage: String? = null,
)

/** Returned to the device that creates/joins — carries the sync credential. */
@Serializable
data class HouseholdDto(
    @SerialName("household_id") val householdId: String,
    @SerialName("invite_code") val inviteCode: String,
    val name: String = "",
    val settings: HouseholdSettings = HouseholdSettings(),
)

/** One entry in the join picker (no invite_code / PIN). */
/** Open Food Facts product response: status 1 + product when found, else status 0. */
@Serializable
data class OffResponseDto(
    val status: Int = 0,
    val product: OffProductDto? = null,
)

@Serializable
data class OffProductDto(
    @SerialName("product_name_de") val productNameDe: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
)

@Serializable
data class HouseholdSummaryDto(
    val id: String,
    val name: String,
    val settings: HouseholdSettings = HouseholdSettings(),
    val protected: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateHouseholdRequest(
    val name: String,
    val settings: HouseholdSettings = HouseholdSettings(),
    val pin: String? = null,
    /** Optional: sets the server's admin password (gates backup/restore) on first use. */
    @SerialName("admin_password") val adminPassword: String? = null,
)

@Serializable
data class JoinHouseholdRequest(val pin: String? = null)

// --- Server admin (backup/restore) ---

@Serializable
data class AdminStatusDto(val configured: Boolean = false)

@Serializable
data class AdminPasswordChangeDto(
    @SerialName("current_password") val currentPassword: String? = null,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class BackupInfoDto(
    val id: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
)

@Serializable
data class BackupListDto(val backups: List<BackupInfoDto> = emptyList())

@Serializable
data class RestoreRequestDto(@SerialName("backup_id") val backupId: String)

@Serializable
data class RestoreResultDto(
    val restored: Boolean = false,
    @SerialName("restart_recommended") val restartRecommended: Boolean = true,
)

@Serializable
data class PatchHouseholdRequest(
    val name: String? = null,
    val settings: HouseholdSettings? = null,
    val pin: String? = null,
)

@Serializable
data class CreateJobResponseDto(
    @SerialName("job_id") val jobId: String,
    val status: String,
)

@Serializable
data class JobResponseDto(
    @SerialName("job_id") val jobId: String,
    val status: String,
    /** Coarse stage key while processing (e.g. "reading_text"); null otherwise. */
    val stage: String? = null,
    /** Populated once status == "done": the extracted schema.org recipes. */
    val result: List<RecipeDto>? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RecipeDto(
    @SerialName("@context") val context: String? = null,
    @SerialName("@type") val type: String? = null,
    val name: String? = null,
    val recipeYield: String? = null,
    /** Numeric servings the recipe makes (openCook extension). */
    val openCookServings: Int? = null,
    /** Coarse category from the AI (Pasta, Fleisch, …) for meal-plan variety. */
    val openCookCategory: String? = null,
    val recipeIngredient: List<String> = emptyList(),
    val recipeInstructions: List<HowToStepDto> = emptyList(),
    val image: List<String> = emptyList(),
    /** Structured ingredients — preferred over the flattened recipeIngredient. */
    val openCookIngredients: List<IngredientDto> = emptyList(),
    val openCookNotes: List<String> = emptyList(),
    /** AI search tags (e.g. "vegetarisch", "schnell", "asiatisch"). */
    val openCookTags: List<String> = emptyList(),
    val prepTime: String? = null,
    val cookTime: String? = null,
    val totalTime: String? = null,
    val nutrition: NutritionDto? = null,
    /** Source cookbook name (openCook extension; schema.org/Recipe has no such property). */
    val cookbook: String? = null,
)

@Serializable
data class HowToStepDto(
    @SerialName("@type") val type: String? = null,
    val text: String = "",
)

@Serializable
data class IngredientDto(
    val quantity: Double? = null,
    val unit: String? = null,
    val name: String = "",
)

/** Nutrition values arrive as display strings with units ("560 kcal", "17 g"). */
@Serializable
data class NutritionDto(
    @SerialName("@type") val type: String? = null,
    val calories: String? = null,
    val proteinContent: String? = null,
    val fatContent: String? = null,
    val carbohydrateContent: String? = null,
    val fiberContent: String? = null,
    val sugarContent: String? = null,
    val openCookBasis: String? = null,
)

/** Response of `POST /images`: the bare server filename the image was stored under. */
@Serializable
data class ImageUploadResponseDto(val name: String)
