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
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.food.opencook.data.backup.BackupFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** The single place that hands deferred work to WorkManager. */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Enqueues the upload → poll chain for a scan. Both steps need a home network (the
     * AI server is a LAN address) and back off exponentially; expedited so they start
     * promptly but fall back to normal work if the foreground-service quota is
     * exhausted. Unique per local job id so a re-trigger collapses rather than
     * duplicating.
     */
    fun scheduleScan(localJobId: String) {
        val input = workDataOf(UploadJobWorker.KEY_LOCAL_JOB_ID to localJobId)
        val constraints = homeNetworkConstraints()

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

    /**
     * (Re)schedule the automatic local backup. Charging + battery-not-low keeps a large
     * write off the user's active day; deliberately **no** network constraint, since
     * writing a file to local storage needs none.
     */
    fun scheduleLocalBackup(frequency: BackupFrequency) {
        val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(Duration.ofDays(frequency.days))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(30))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LocalBackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Sync in the background even while the app is closed. The server is typically a
     * desktop that is off most of the day, so rather than one hopeful moment we spread
     * cheap attempts across it — and only on a network that could reach it at all.
     */
    fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(3))
            .setConstraints(homeNetworkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BACKGROUND_SYNC_NAME,
            // UPDATE, not KEEP: an installed app must pick up changed constraints
            // (Wi-Fi/VPN instead of "any network") without a reinstall.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelLocalBackup() {
        WorkManager.getInstance(context).cancelUniqueWork(LocalBackupWorker.UNIQUE_NAME)
    }

    private fun uniqueName(localJobId: String) = "scan-$localJobId"

    private companion object {
        const val BACKGROUND_SYNC_NAME = "opencook-background-sync"
    }
}

/**
 * Constraint for everything that talks to the household: the server and peer phones
 * live on the LAN, so "any connection" is the wrong bar — on mobile data such a job
 * can only run into a timeout and then back off. Wi-Fi and Ethernet qualify, and so
 * does a VPN tunnel (the supported way in from outside). Mirrors the gate the
 * foreground sync applies through LanMonitor.
 *
 * [NetworkType.CONNECTED] is only the legacy fallback WorkManager wants alongside the
 * request; the request itself is what the scheduler enforces here (minSdk 30).
 */
private fun homeNetworkConstraints(): Constraints {
    val request = NetworkRequest.Builder()
        // The builder requires NOT_VPN by default, which would exclude the tunnel case.
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        .build()
    return Constraints.Builder()
        .setRequiredNetworkRequest(request, NetworkType.CONNECTED)
        .build()
}
