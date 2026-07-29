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

package com.food.opencook.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.sync.SyncManager
import com.food.opencook.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * What the AI-backed entry points (photo, gallery) can do right now. Extraction runs
 * on the server, so a serverless household (peer-to-peer or standalone) can't use them
 * at all, and a configured-but-offline server means a scan waits in the queue.
 */
data class ScanUiState(
    /** False for serverless households — photo/gallery stay visible but disabled. */
    val serverConfigured: Boolean = false,
    /** Null until the first sync round finished; only meaningful with a server. */
    val serverReachable: Boolean? = null,
    /** Scans already taken that are still waiting to be uploaded. */
    val queuedScans: Int = 0,
) {
    /** Server there but not answering — a new scan will queue instead of running now. */
    val serverOffline: Boolean get() = serverConfigured && serverReachable == false
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val scheduler: WorkScheduler,
    private val imageStore: ImageStore,
    private val syncManager: SyncManager,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<ScanUiState> =
        combine(
            settings.serverUrl.map { !it.isNullOrBlank() },
            syncManager.serverReachable,
            // A job without a server id has not been uploaded yet — that's the queue.
            repository.observeActiveJobs().map { jobs -> jobs.count { it.serverJobId == null } },
        ) { configured, reachable, queued -> ScanUiState(configured, reachable, queued) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState())

    /** Re-probe the server after the user tapped "try again" on the offline hint. */
    fun retryServerCheck() = syncManager.syncNow()

    fun newCaptureFile(): File = imageStore.newCaptureFile()

    /** Register & schedule a scan for an already-saved local file, then report its job id. */
    fun startScan(localImagePath: String, onJobCreated: (String) -> Unit) {
        viewModelScope.launch {
            val jobId = repository.createLocalJob(localImagePath)
            scheduler.scheduleScan(jobId)
            onJobCreated(jobId)
        }
    }

    /** Copy a picked gallery image locally, then start the scan. */
    fun startScanFromUri(uri: Uri, onJobCreated: (String) -> Unit) {
        viewModelScope.launch {
            val path = imageStore.saveFromUri(uri)
            val jobId = repository.createLocalJob(path)
            scheduler.scheduleScan(jobId)
            onJobCreated(jobId)
        }
    }
}
