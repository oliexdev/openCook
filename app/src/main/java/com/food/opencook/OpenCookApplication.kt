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
