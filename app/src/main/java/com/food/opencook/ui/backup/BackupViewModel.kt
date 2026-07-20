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

package com.food.opencook.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.backup.AutoBackupConfig
import com.food.opencook.data.backup.BackupFolder
import com.food.opencook.data.backup.BackupFrequency
import com.food.opencook.data.backup.BackupManifest
import com.food.opencook.data.backup.BackupSettings
import com.food.opencook.data.backup.BackupState
import com.food.opencook.data.backup.LocalBackupManager
import com.food.opencook.data.backup.backupFileName
import com.food.opencook.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** An archive the user picked, held while the confirmation dialog is up. */
data class PendingRestore(val uri: Uri, val manifest: BackupManifest)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: LocalBackupManager,
    private val settings: BackupSettings,
    private val folder: BackupFolder,
    private val scheduler: WorkScheduler,
) : ViewModel() {

    val state: StateFlow<BackupState> = manager.state

    val config: StateFlow<AutoBackupConfig> = settings.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupConfig())

    /** Display name of the chosen folder, resolved lazily for the settings row. */
    val folderName: StateFlow<String?> = settings.config
        .map { cfg -> cfg.folderUri?.let { folder.displayName(Uri.parse(it)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pending = MutableStateFlow<PendingRestore?>(null)
    val pending: StateFlow<PendingRestore?> = _pending.asStateFlow()

    private val _peekError = MutableStateFlow<String?>(null)
    val peekError: StateFlow<String?> = _peekError.asStateFlow()

    /** Suggested name for the file picker. */
    fun suggestedFileName(): String = backupFileName()

    fun export(target: Uri) = manager.export(target)

    /** Read the manifest first so the user confirms against real numbers, not a guess. */
    fun offerRestore(source: Uri) {
        viewModelScope.launch {
            manager.peek(source)
                .onSuccess { _pending.value = PendingRestore(source, it) }
                .onFailure { _peekError.value = it.message }
        }
    }

    fun confirmRestore() {
        val pending = _pending.value ?: return
        _pending.value = null
        manager.restore(pending.uri)
    }

    fun cancelRestore() {
        _pending.value = null
    }

    fun dismissPeekError() {
        _peekError.value = null
    }

    fun clearResult() = manager.clearResult()

    /** Turn automatic backup on with a freshly picked folder, or off. */
    fun setAutoFolder(treeUri: Uri?) {
        viewModelScope.launch {
            if (treeUri == null) {
                settings.setFolder(null)
                scheduler.cancelLocalBackup()
            } else {
                folder.persistPermission(treeUri)
                settings.setFolder(treeUri.toString())
                scheduler.scheduleLocalBackup(settings.configOnce().frequency)
            }
        }
    }

    fun setFrequency(frequency: BackupFrequency) {
        viewModelScope.launch {
            settings.setFrequency(frequency)
            if (settings.configOnce().enabled) scheduler.scheduleLocalBackup(frequency)
        }
    }
}
