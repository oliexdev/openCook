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
