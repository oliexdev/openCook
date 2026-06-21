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

package com.food.opencook.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/** A server found on the LAN via mDNS. [host] is a resolved IP, ready for OkHttp. */
data class DiscoveredServer(val name: String, val host: String, val port: Int)

/**
 * Discovers openCook servers on the local network via Android NSD (mDNS).
 *
 * Only ever collected **while the onboarding screen is open** (or as a one-shot to
 * refresh a stale address) — never as a background service, so it costs no battery
 * when idle. NSD does NOT work on the standard emulator (isolated NAT); use the
 * manual-address fallback there.
 */
@Singleton
class ServerDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Emits the current set of discovered servers, updated as they come and go. */
    fun discover(): Flow<List<DiscoveredServer>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        // Without a multicast lock many devices drop the inbound mDNS packets.
        val multicastLock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("opencook-nsd")
            .apply { setReferenceCounted(false); acquire() }

        val found = mutableMapOf<String, DiscoveredServer>()
        fun emit() {
            synchronized(found) { trySend(found.values.toList()) }
        }
        trySend(emptyList()) // so the UI shows "searching" rather than nothing

        // API 34+ deprecates resolveService in favour of a per-service callback.
        val infoCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
        // Direct (inline) executor: NsdManager runs the callback on its own thread.
        // A real thread-pool would crash with RejectedExecutionException if we shut it
        // down while NsdManager still has a queued message — nothing to shut down here.
        val executor = Executor { it.run() }

        fun store(info: NsdServiceInfo) {
            val host = info.hostAddressString() ?: return
            synchronized(found) { found[info.serviceName] = DiscoveredServer(info.serviceName, host, info.port) }
            emit()
        }

        fun resolve(serviceInfo: NsdServiceInfo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val cb = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                    override fun onServiceInfoCallbackUnregistered() {}
                    override fun onServiceLost() {}
                    override fun onServiceUpdated(info: NsdServiceInfo) = store(info)
                }
                infoCallbacks += cb
                runCatching { nsdManager.registerServiceInfoCallback(serviceInfo, executor, cb) }
            } else {
                // A ResolveListener must NOT be reused concurrently -> one per service.
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) = store(info)
                })
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) = resolve(serviceInfo)
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found.remove(serviceInfo.serviceName) }
                emit()
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                infoCallbacks.forEach { cb -> runCatching { nsdManager.unregisterServiceInfoCallback(cb) } }
            }
            runCatching { multicastLock.release() }
        }
    }

    /**
     * One-shot: the first server seen within [timeoutMs], else null. Used to refresh
     * a stale stored address (e.g. the server got a new DHCP IP) when a sync fails.
     * Returns null quickly when off-LAN or the server is down, so it's cheap.
     */
    suspend fun discoverFirst(timeoutMs: Long = 4_000): DiscoveredServer? =
        withTimeoutOrNull(timeoutMs) {
            discover().firstOrNull { it.isNotEmpty() }?.firstOrNull()
        }

    private companion object {
        const val SERVICE_TYPE = "_opencook._tcp."
    }
}

@Suppress("DEPRECATION")
private fun NsdServiceInfo.hostAddressString(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        hostAddresses.firstOrNull()?.hostAddress
    } else {
        host?.hostAddress
    }
