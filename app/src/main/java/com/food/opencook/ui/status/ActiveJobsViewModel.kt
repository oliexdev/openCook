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

package com.food.opencook.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.food.opencook.data.local.entity.JobEntity
import com.food.opencook.repository.RecipeRepository
import com.food.opencook.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class StripMode { HIDDEN, ACTIVE, FAILED, FINISHED }

/** State of the status strip shown above the bottom navigation. */
data class StatusStripUiState(
    val processingCount: Int = 0,
    val queuedCount: Int = 0,
    /** Stage key of the oldest *processing* job (null while merely queued). */
    val oldestStageKey: String? = null,
    val failedCount: Int = 0,
    /** Names of recipes from finished, unacknowledged scans. */
    val finishedRecipeNames: List<String> = emptyList(),
) {
    val activeCount: Int get() = processingCount + queuedCount

    /** What the strip should display; active progress wins, then failures, then results. */
    val mode: StripMode get() = when {
        activeCount > 0 -> StripMode.ACTIVE
        failedCount > 0 -> StripMode.FAILED
        finishedRecipeNames.isNotEmpty() -> StripMode.FINISHED
        else -> StripMode.HIDDEN
    }
}

/** Pure mapping (unit-tested) from raw job/recipe data to strip state. */
fun statusStripState(
    active: List<JobEntity>,
    failed: List<JobEntity>,
    finishedRecipeNames: List<String>,
): StatusStripUiState {
    val processing = active.filter { it.status == "processing" }
    val queued = active.filter { it.status != "processing" }
    return StatusStripUiState(
        processingCount = processing.size,
        queuedCount = queued.size,
        oldestStageKey = processing.firstOrNull()?.stage, // active is ordered createdAt ASC
        failedCount = failed.size,
        finishedRecipeNames = finishedRecipeNames,
    )
}

/**
 * App-scoped owner of scan progress. Polls all active jobs while the app is in the
 * foreground (the background WorkManager poll covers the rest) and exposes the
 * aggregate state for the status strip. Polling lives here — not per screen — so
 * the user can keep working while scans process.
 */
@HiltViewModel
class ActiveJobsViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val scheduler: WorkScheduler,
) : ViewModel() {

    val uiState: StateFlow<StatusStripUiState> = combine(
        repository.observeActiveJobs(),
        repository.observeFailedJobs(),
        repository.observeUnreviewedRecipes(),
    ) { active, failed, unreviewed ->
        statusStripState(active, failed, unreviewed.map { it.recipe.name?.takeIf(String::isNotBlank) ?: "Rezept" })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatusStripUiState())

    init {
        pollLoop()
    }

    private fun pollLoop() = viewModelScope.launch {
        while (isActive) {
            val active = repository.getActiveJobs()
            for (job in active) {
                try {
                    repository.refreshJob(job.jobId)
                } catch (_: IOException) {
                    // Server unreachable — job stays queued; retry next round.
                }
            }
            delay(if (active.isEmpty()) IDLE_POLL_MS else ACTIVE_POLL_MS)
        }
    }

    /** Re-run failed scans as fresh jobs (same photo) and clear the failed ones. */
    fun retryFailed() = viewModelScope.launch {
        repository.getFailedJobs().forEach { job ->
            val newId = repository.createLocalJob(job.localImagePath)
            scheduler.scheduleScan(newId)
        }
        repository.acknowledgeFinishedJobs()
    }

    /** Cancel all running scans: stop their workers and drop the local jobs. */
    fun cancelActive() = viewModelScope.launch {
        repository.getActiveJobs().forEach { job ->
            scheduler.cancelScan(job.jobId)
            repository.deleteJob(job.jobId)
        }
    }

    fun acknowledgeFinished() = viewModelScope.launch { repository.acknowledgeFinishedJobs() }

    private companion object {
        const val ACTIVE_POLL_MS = 2_000L
        const val IDLE_POLL_MS = 3_000L
    }
}
