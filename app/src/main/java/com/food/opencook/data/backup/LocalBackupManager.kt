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

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the export/restore is doing right now. */
sealed interface BackupState {
    data object Idle : BackupState
    data class Running(val phase: BackupPhase, val fraction: Float, val restoring: Boolean) : BackupState
    data class Exported(val manifest: BackupManifest) : BackupState
    data class Restored(val result: BackupImportResult) : BackupState
    data class Failed(val reason: BackupRejected?, val message: String?) : BackupState
}

/**
 * Runs exports and restores on an application-scoped coroutine, so a several-hundred-MB
 * archive survives the user navigating away or rotating the device — the same reason
 * [com.food.opencook.sync.SyncManager] is app-scoped rather than owned by a ViewModel.
 * The UI only collects [state].
 */
@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: BackupWriter,
    private val importer: BackupImporter,
    private val json: kotlinx.serialization.json.Json,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private var running: Job? = null

    val busy: Boolean get() = running?.isActive == true

    /** Write a backup into a document the user just created via the file picker. */
    fun export(target: Uri) {
        if (busy) return
        running = scope.launch {
            _state.value = BackupState.Running(BackupPhase.RECIPES, 0f, restoring = false)
            runCatching {
                val out = context.contentResolver.openOutputStream(target)
                    ?: error("Cannot open $target")
                out.use { stream ->
                    writer.write(stream) { phase, fraction ->
                        _state.value = BackupState.Running(phase, fraction, restoring = false)
                    }
                }
            }.onSuccess {
                _state.value = BackupState.Exported(it)
            }.onFailure { error ->
                // Never leave a truncated file behind that the user would trust as a backup.
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, target) }
                _state.value = BackupState.Failed(reason = null, message = error.message)
            }
        }
    }

    /** Restore from a document the user picked. Additive and idempotent — see [BackupImporter]. */
    fun restore(source: Uri) {
        if (busy) return
        running = scope.launch {
            _state.value = BackupState.Running(BackupPhase.IMAGES, 0f, restoring = true)
            runCatching {
                importer.import(
                    open = {
                        context.contentResolver.openInputStream(source) ?: error("Cannot open $source")
                    },
                    onProgress = { phase, fraction ->
                        _state.value = BackupState.Running(phase, fraction, restoring = true)
                    },
                )
            }.onSuccess {
                _state.value = BackupState.Restored(it)
            }.onFailure { error ->
                _state.value = BackupState.Failed(
                    reason = (error as? BackupRejectedException)?.reason,
                    message = error.message,
                )
            }
        }
    }

    /** Peek at an archive's manifest so the UI can confirm before writing anything. */
    suspend fun peek(source: Uri): Result<BackupManifest> = runCatching {
        val reader = BackupReader(json)
        val input = context.contentResolver.openInputStream(source) ?: error("Cannot open $source")
        input.use { reader.readManifest(it) }
    }

    fun clearResult() {
        if (_state.value !is BackupState.Running) _state.value = BackupState.Idle
    }
}
