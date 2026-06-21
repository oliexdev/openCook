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

package com.food.opencook.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.food.opencook.data.notification.JobNotifier
import com.food.opencook.repository.JobOutcome
import com.food.opencook.repository.RecipeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Background resilience poll: checks a job until it finishes, then drains the
 * recipes and notifies the user. Re-runs via WorkManager backoff while the job is
 * still pending/processing. This is the fallback for when the app isn't in the
 * foreground; the active in-screen poll handles the visible case. Both funnel
 * through the repository's idempotent drain, so no duplicate drafts.
 */
@HiltWorker
class PollJobWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecipeRepository,
    private val notifier: JobNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val localJobId = inputData.getString(UploadJobWorker.KEY_LOCAL_JOB_ID) ?: return Result.failure()
        return try {
            when (val outcome = repository.refreshJob(localJobId)) {
                is JobOutcome.Done -> {
                    if (outcome.newlyDrained) notifier.notifyRecipeReady(localJobId)
                    Result.success()
                }
                is JobOutcome.Failed -> Result.success() // terminal; status recorded on the job
                JobOutcome.Pending, JobOutcome.Processing -> Result.retry()
            }
        } catch (_: IOException) {
            Result.retry() // server unreachable — keep waiting
        }
    }
}
