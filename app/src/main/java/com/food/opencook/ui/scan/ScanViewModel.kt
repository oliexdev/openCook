package com.food.opencook.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.image.ImageStore
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val scheduler: WorkScheduler,
    private val imageStore: ImageStore,
    settings: SettingsRepository,
) : ViewModel() {

    /** Without a server address the scan can never be uploaded, so we gate on it. */
    val serverConfigured: StateFlow<Boolean> =
        settings.serverUrl
            .map { !it.isNullOrBlank() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
