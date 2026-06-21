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

package com.food.opencook.ui.recipeimport

import android.content.Context
import android.net.Uri
import com.food.opencook.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.recipeimport.ImportedRecipe
import com.food.opencook.data.recipeimport.JsonLdExtractor
import com.food.opencook.data.recipeimport.RecipeBundle
import com.food.opencook.data.recipeimport.SourceCookbook
import com.food.opencook.data.remote.mapper.toMappedRecipe
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.repository.SaveResult
import com.food.opencook.share.ShareImportBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject

sealed interface ImportState {
    data object Idle : ImportState
    data class Running(val done: Int, val total: Int) : ImportState
    data class Done(val imported: Int, val skipped: Int) : ImportState
    data class Error(val message: String) : ImportState
}

/** Outcome of a single share-import (one recipe shared from a web page). */
sealed interface ShareImportState {
    data object Idle : ShareImportState
    data object Fetching : ShareImportState
    data class Saved(val name: String, val recipeId: String) : ShareImportState
    data class Duplicate(val name: String) : ShareImportState
    data object NoRecipe : ShareImportState
    data class Error(val message: String) : ShareImportState
}

/**
 * Imports recipes from JSON (a picked file or pasted text). Parsing + saving run off
 * the main thread; each recipe goes through the same [RecipeRepository.saveRecipe] path
 * as AI-extracted ones, so imports sync identically. An optional [limit] caps how many
 * are imported (useful for a quick test of a very large file).
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val json: Json,
    private val repository: RecipeRepository,
    private val imageStore: ImageStore,
    private val shareImportBus: ShareImportBus,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _shareState = MutableStateFlow<ShareImportState>(ShareImportState.Idle)
    val shareState: StateFlow<ShareImportState> = _shareState.asStateFlow()

    /** A URL shared into the app, awaiting import (null when none). */
    val pendingShareUrl: StateFlow<String?> = shareImportBus.url

    fun importFromUri(uri: Uri, limit: Int?) = launchImport(limit) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error(context.getString(R.string.import_read_failed))
    }

    fun reset() { _state.value = ImportState.Idle }

    fun resetShare() { _shareState.value = ShareImportState.Idle }

    /**
     * Import a single recipe from a shared web page URL: fetch the HTML, pull the
     * schema.org/Recipe out of its JSON-LD, and save it through the same path as the
     * file import. Mirrors the browser extension, but native. Clears the share bus up
     * front so the same URL isn't reprocessed.
     */
    fun importFromUrl(pageUrl: String) {
        if (_shareState.value is ShareImportState.Fetching) return
        shareImportBus.clear()
        viewModelScope.launch {
            _shareState.value = ShareImportState.Fetching
            _shareState.value = runCatching {
                val html = fetchHtml(pageUrl)
                    ?: return@runCatching ShareImportState.Error(
                        context.getString(R.string.import_share_error_load),
                    )
                val parsed = withContext(Dispatchers.Default) {
                    JsonLdExtractor.extractFirstRecipe(html, json)
                } ?: return@runCatching ShareImportState.NoRecipe
                // Group under the source site (Chefkoch/NDR/…) unless the page already names a
                // real cookbook via schema.org isPartOf — that always wins.
                val cookbook = parsed.dto.cookbook?.takeIf { it.isNotBlank() }
                    ?: SourceCookbook.fromUrl(pageUrl)
                val imp = parsed.copy(dto = parsed.dto.copy(cookbook = cookbook))
                val info = saveOne(imp, System.currentTimeMillis())
                when (info.result) {
                    SaveResult.Saved -> ShareImportState.Saved(info.name, info.recipeId)
                    SaveResult.Duplicate -> ShareImportState.Duplicate(info.name)
                }
            }.getOrElse {
                ShareImportState.Error(it.message ?: context.getString(R.string.import_generic_failed))
            }
        }
    }

    private fun launchImport(limit: Int?, read: suspend () -> ByteArray) {
        if (_state.value is ImportState.Running) return
        viewModelScope.launch {
            _state.value = ImportState.Running(0, 0)
            _state.value = runCatching {
                val bytes = withContext(Dispatchers.IO) { read() }
                var recipes = withContext(Dispatchers.Default) { RecipeBundle.read(bytes, json) }
                if (limit != null && limit > 0) recipes = recipes.take(limit)
                val total = recipes.size
                var imported = 0
                var skipped = 0
                val now = System.currentTimeMillis()
                recipes.forEachIndexed { index, imp ->
                    val result = runCatching { saveOne(imp, now) }.getOrNull()
                    // Saved → counted; Duplicate or a parse/save error → skipped.
                    if (result?.result == SaveResult.Saved) imported++ else skipped++
                    if ((index + 1) % 20 == 0 || index + 1 == total) {
                        _state.value = ImportState.Running(index + 1, total)
                    }
                }
                ImportState.Done(imported, skipped)
            }.getOrElse {
                ImportState.Error(it.message ?: context.getString(R.string.import_generic_failed))
            }
        }
    }

    private suspend fun saveOne(imp: ImportedRecipe, now: Long): SavedRecipeInfo {
        // Resolve the dish image: embedded/zip bytes, else an http(s) URL fetched best-effort.
        val bytes = imp.imageBytes ?: imp.imageUrl?.let { fetchImage(it) }
        // Drop the raw image refs before mapping (the mapper would treat them as server image
        // names); attach our own owned local file instead.
        val mapped = imp.dto.copy(image = emptyList()).toMappedRecipe(sourcePhotoId = null, now = now)
        val images = if (bytes != null) {
            listOf(
                ImageEntity(
                    id = UUID.randomUUID().toString(),
                    recipeId = mapped.recipe.id,
                    position = 0,
                    remoteName = null,
                    localPath = imageStore.saveBytes(bytes),
                    isPrimary = true,
                ),
            )
        } else {
            mapped.images
        }
        val result = repository.saveRecipe(
            mapped.recipe, mapped.ingredients, mapped.instructions, mapped.nutrition, images,
        )
        return SavedRecipeInfo(
            result,
            mapped.recipe.id,
            mapped.recipe.name ?: context.getString(R.string.import_share_fallback_name),
        )
    }

    /** Best-effort download of an http(s) image (≤10 MB, short timeouts); null on any failure. */
    private suspend fun fetchImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.use { it.readBytes().takeIf { b -> b.size in 1..(10 * 1024 * 1024) } }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** Best-effort fetch of a web page's HTML (≤5 MB, short timeouts); null on any failure. */
    private suspend fun fetchHtml(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                // A desktop UA: some sites serve a stripped page (no JSON-LD) to unknown clients.
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                if (conn.responseCode !in 200..299) null
                else conn.inputStream.use {
                    it.readBytes().takeIf { b -> b.size in 1..(5 * 1024 * 1024) }?.decodeToString()
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

}

/** [RecipeRepository.saveRecipe] result plus the saved recipe's id/name, for UI feedback. */
private data class SavedRecipeInfo(val result: SaveResult, val recipeId: String, val name: String)
