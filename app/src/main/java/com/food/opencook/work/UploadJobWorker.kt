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
import com.food.opencook.repository.RecipeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Uploads a queued scan's photo to the server (POST /jobs). Retries with backoff
 * while the server is unreachable, so an offline scan stays "pending" and is sent
 * automatically once connectivity returns. Idempotent via the repository.
 */
@HiltWorker
class UploadJobWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecipeRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val localJobId = inputData.getString(KEY_LOCAL_JOB_ID) ?: return Result.failure()
        return try {
            repository.uploadJob(localJobId)
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_LOCAL_JOB_ID = "local_job_id"
        private const val MAX_ATTEMPTS = 5
    }
}
