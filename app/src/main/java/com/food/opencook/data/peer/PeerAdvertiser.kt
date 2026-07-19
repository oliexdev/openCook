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

package com.food.opencook.data.peer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.food.opencook.data.discovery.ServerDiscovery
import com.food.opencook.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes this phone answerable for peer-to-peer sync — gated on the household's P2P
 * switch ([SettingsRepository.p2pEnabled]) and on one of two presence windows:
 * **app in the foreground**, or the **standby service** ([PeerStandbyService])
 * that keeps the phone reachable with the app closed. In both cases only with an
 * **active Wi-Fi network** (never expose the listener via mobile data; deliberately
 * no SSID matching, which would drag in the location permission).
 *
 * Within that window it starts the embedded [PeerSyncServer] on an ephemeral port
 * and registers `_opencook._tcp` with TXT `role=peer` via NSD, so other phones find
 * this one through the exact same discovery path they already use for the desktop
 * server. Everything is torn down when the last window closes or Wi-Fi drops.
 *
 * Also drives the standby service itself: whenever the P2P switch + household state
 * (or the app's foreground state — the retry window for a start that wasn't allowed
 * from the background) change, the service is started or stopped to match.
 */
@Singleton
class PeerAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val peerSyncServer: PeerSyncServer,
    private val settings: SettingsRepository,
    private val serverDiscovery: ServerDiscovery,
) : DefaultLifecycleObserver {

    // Plain IO pool. (A limitedParallelism(1) lane looked attractive for strict
    // FIFO ordering of bring-up/tear-down, but in practice the resume of nested
    // withContext(IO) calls starved on the lane and bring-up never finished —
    // the mutex below is what actually serialises the transitions.)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex() // serialises bring-up/tear-down transitions
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val foreground = MutableStateFlow(false)
    private val standby = MutableStateFlow(false)

    /** Called once from the Application (main thread). Both collectors live for the
     *  whole process; the state flows gate the actual listener. */
    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            combine(foreground, standby, wifiAvailable(), settings.householdCode, settings.p2pEnabled) { fg, sb, wifi, code, p2p ->
                p2p && (fg || sb) && wifi && !code.isNullOrBlank()
            }
                .distinctUntilChanged()
                .collect { shouldRun ->
                    // Log-and-swallow here (incl. cancellation) so one failed transition
                    // neither kills the collector nor vanishes without a trace.
                    runCatching { if (shouldRun) bringUp() else tearDown() }
                        .onFailure { Log.w(TAG, "advertiser transition failed (shouldRun=$shouldRun)", it) }
                }
        }
        scope.launch {
            combine(settings.p2pEnabled, settings.householdCode, foreground) { p2p, code, _ ->
                p2p && !code.isNullOrBlank()
            }
                // No distinctUntilChanged on purpose: every foreground flip re-emits, so
                // a service start that was denied while backgrounded is retried on the
                // next app open.
                .collect { shouldRun -> PeerStandbyService.ensure(context, shouldRun) }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground.value = false
    }

    /** Held true by [PeerStandbyService] between its create and destroy. */
    fun setStandby(active: Boolean) {
        standby.value = active
    }

    private suspend fun bringUp() = mutex.withLock {
        if (registrationListener != null) return@withLock // already advertising
        val port = peerSyncServer.start()
        if (port == null) {
            Log.w(TAG, "bringUp: embedded server did not start — not advertising")
            return@withLock
        }
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            // The model makes a friendly default label in the join picker and the
            // "last synced with …" status; NSD auto-renames on collision ("(2)").
            serviceName = Build.MODEL
            // Registration wants the bare type WITHOUT the trailing dot — with it, some
            // stacks (Samsung) drop the registration silently, no callback at all.
            // (Discovery in ServerDiscovery keeps the dotted form, which works there.)
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("role", "peer")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registered: NsdServiceInfo) {
                // NSD may have renamed us on conflict — remember the *confirmed* name
                // so discovery can filter out our own advertisement.
                serverDiscovery.ownServiceName = registered.serviceName
                Log.d(TAG, "advertising as '${registered.serviceName}' on port $port")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed, errorCode=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure {
                // Advertising failed but the HTTP server is up: peers can still connect
                // via a manually entered address, so leave the server running.
                Log.w(TAG, "registerService threw", it)
                registrationListener = null
            }
    }

    private suspend fun tearDown() = mutex.withLock {
        registrationListener?.let { listener ->
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
        serverDiscovery.ownServiceName = null
        peerSyncServer.stop()
    }

    /** Emits whether an active Wi-Fi network is up, live-updated via a network callback. */
    private fun wifiAvailable() = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun currentlyOnWifi(): Boolean =
            cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        trySend(currentlyOnWifi())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(currentlyOnWifi())
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }

    private companion object {
        // No trailing dot — see the registration comment above.
        const val SERVICE_TYPE = "_opencook._tcp"
        const val TAG = "OpenCookP2P"
    }
}
