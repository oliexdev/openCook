package com.food.opencook.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.food.opencook.sync.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background sync that runs even when the app is closed. The self-hosted server is
 * the user's desktop PC, which is frequently off — so each run is a cheap, fail-fast
 * attempt (the sync call itself probes reachability) and we simply wait for the next
 * periodic tick rather than backing off. WorkManager spreads the periodic runs across
 * the day (with a CONNECTED constraint), so it eventually catches a window when the
 * desktop is on. A failed attempt costs ~one short connection — no scanning, no drain.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Always succeed: a server-down failure must NOT trigger backoff — the next
        // periodic run (or connectivity trigger) retries on schedule.
        runCatching { syncEngine.sync() }
        return Result.success()
    }
}
