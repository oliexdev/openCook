package com.food.opencook.sync

import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.recipeimport.RecipeImportParser
import com.food.opencook.data.recipeimport.SourceCookbook
import com.food.opencook.data.remote.ImportApi
import com.food.opencook.data.remote.mapper.toMappedRecipe
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.SaveResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the server's recipe-import inbox (recipes the browser extension scraped and
 * pushed). Runs after a successful [SyncEngine] pass, while the server is known reachable.
 *
 * Each pending import is claimed atomically — only the winning device materializes it,
 * so several devices syncing at once never create duplicates. A claimed recipe goes
 * through the same [RecipeRepository.saveRecipe] path as a file import, so it appends
 * sync messages and propagates to every device. The image (if any) was already stored
 * server-side under [PendingImportDto.imageName]; we attach it as a [remoteName] so it
 * loads via GET /images/{name}, exactly like an AI photo crop. Only on a fully successful
 * save do we consume the import; otherwise it reverts to pending (server TTL) and retries.
 */
@Singleton
class ImportInboxSyncer @Inject constructor(
    private val importApi: ImportApi,
    private val repository: RecipeRepository,
    private val settings: SettingsRepository,
    private val json: Json,
) {

    /** How a drain pass went, so the UI can report imports and skipped duplicates. */
    data class Result(val imported: Int, val duplicates: Int) {
        val any: Boolean get() = imported > 0 || duplicates > 0
    }

    suspend fun drain(): Result {
        val code = settings.householdCodeOnce()?.takeIf { it.isNotBlank() } ?: return Result(0, 0)
        val pending = runCatching { importApi.pending(code) }.getOrNull() ?: return Result(0, 0)

        var imported = 0
        var duplicates = 0
        val now = System.currentTimeMillis()
        for (item in pending.imports) {
            val claim = runCatching { importApi.claim(item.id, code) }.getOrNull()
            if (claim == null || !claim.isSuccessful) continue // 409 (claimed elsewhere) or offline

            val result = runCatching { materialize(item.recipe, item.imageName, item.sourceUrl, now) }.getOrNull()
            when (result) {
                SaveResult.Saved -> { runCatching { importApi.consume(item.id, code) }; imported++ }
                // Duplicate of an existing recipe: don't keep it, but consume so it stops recurring.
                SaveResult.Duplicate -> { runCatching { importApi.consume(item.id, code) }; duplicates++ }
                // null = parse/save error: leave claimed; reverts to pending after the server TTL.
                null -> {}
            }
        }
        return Result(imported, duplicates)
    }

    private suspend fun materialize(
        recipe: JsonElement,
        imageName: String?,
        sourceUrl: String?,
        now: Long,
    ): SaveResult {
        val text = json.encodeToString(JsonElement.serializer(), recipe)
        val parsed = RecipeImportParser.parse(text, json).firstOrNull()
            ?: error("No recipe parsed from import")
        // Group under the source site (Chefkoch/NDR/…) unless the recipe already names a real
        // cookbook via schema.org isPartOf — that always wins.
        val dto = parsed.copy(
            cookbook = parsed.cookbook?.takeIf { it.isNotBlank() } ?: SourceCookbook.fromUrl(sourceUrl),
        )
        val mapped = dto.toMappedRecipe(sourcePhotoId = null, now = now)
        val images = imageName?.let {
            listOf(
                ImageEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = mapped.recipe.id,
                    position = 0,
                    remoteName = it,
                    isPrimary = true,
                ),
            )
        }.orEmpty()
        return repository.saveRecipe(
            mapped.recipe, mapped.ingredients, mapped.instructions, mapped.nutrition, images,
        )
    }
}
