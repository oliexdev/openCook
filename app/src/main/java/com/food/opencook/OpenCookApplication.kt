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

package com.food.opencook

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.food.opencook.data.localization.LocalizedLists
import com.food.opencook.data.notification.JobNotifier
import com.food.opencook.data.peer.PeerAdvertiser
import com.food.opencook.data.remote.BaseUrlInterceptor
import com.food.opencook.data.settings.SettingsRepository
import com.food.opencook.sync.SyncManager
import com.food.opencook.work.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Application entry point; root of the Hilt dependency graph. */
@HiltAndroidApp
class OpenCookApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var baseUrlInterceptor: BaseUrlInterceptor
    @Inject lateinit var jobNotifier: JobNotifier
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var localizedLists: LocalizedLists
    @Inject lateinit var peerAdvertiser: PeerAdvertiser

    private val appScope = CoroutineScope(SupervisorJob())

    // On-demand WorkManager initialization with the Hilt worker factory (see the
    // InitializationProvider removal in AndroidManifest.xml).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        jobNotifier.ensureChannel()
        // Keep the OkHttp base URL in sync with the user-configured server address.
        settingsRepository.serverUrl
            .onEach { baseUrlInterceptor.setBaseUrl(it) }
            .launchIn(appScope)
        // Load the content-language domain lists (grocery keywords, staples, units) at
        // startup and whenever the household content language changes.
        settingsRepository.contentLanguage
            .onEach { localizedLists.reload() }
            .launchIn(appScope)
        // Auto-sync while the app is alive: initial + periodic + after local changes.
        syncManager.start()
        // Answer peer-to-peer sync (embedded server + mDNS) while foregrounded on Wi-Fi.
        peerAdvertiser.install()
        // Background sync even when the app is closed. The server (a desktop PC) is
        // often off, so we spread cheap attempts across the day and let WorkManager
        // run them only while connected — see SyncWorker.
        scheduleBackgroundSync()
    }

    private fun scheduleBackgroundSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "opencook-background-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
