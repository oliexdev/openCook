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
