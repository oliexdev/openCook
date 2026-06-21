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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the upload → poll chain for a scan. Both steps require connectivity and
 * back off exponentially; expedited so they start promptly but fall back to normal
 * work if the foreground-service quota is exhausted. Unique per local job id so a
 * re-trigger collapses rather than duplicating.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleScan(localJobId: String) {
        val input = workDataOf(UploadJobWorker.KEY_LOCAL_JOB_ID to localJobId)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val upload = OneTimeWorkRequestBuilder<UploadJobWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(10))
            .build()

        val poll = OneTimeWorkRequestBuilder<PollJobWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(5))
            .build()

        WorkManager.getInstance(context)
            .beginUniqueWork(uniqueName(localJobId), ExistingWorkPolicy.KEEP, upload)
            .then(poll)
            .enqueue()
    }

    /** Cancel the upload+poll chain for a scan (user aborted it). */
    fun cancelScan(localJobId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(localJobId))
    }

    private fun uniqueName(localJobId: String) = "scan-$localJobId"
}
