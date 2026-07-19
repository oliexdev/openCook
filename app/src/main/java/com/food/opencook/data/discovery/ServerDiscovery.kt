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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/** What kind of endpoint advertised itself under `_opencook._tcp`. */
enum class DiscoveredRole {
    /** The Python desktop server. Also assumed when the TXT `role` is absent
     *  (servers older than the P2P feature don't send it). */
    SERVER,

    /** Another phone running openCook in the foreground (see PeerSyncServer). */
    PEER,
}

/** A sync endpoint found on the LAN via mDNS. [host] is a resolved IP, ready for OkHttp. */
data class DiscoveredServer(val name: String, val host: String, val port: Int, val role: DiscoveredRole = DiscoveredRole.SERVER) {
    /**
     * Base URL for this endpoint. NSD often resolves phones to an IPv6 address —
     * those need brackets in a URL, and a link-local zone id (`%wlan0`) must go
     * (OkHttp can't parse it; the route is implied on a single-interface phone).
     */
    fun baseUrl(): String {
        val bare = host.substringBefore('%')
        val formatted = if (bare.contains(':')) "[$bare]" else bare
        return "http://$formatted:$port"
    }
}

/**
 * Discovers openCook sync endpoints on the local network via Android NSD (mDNS) —
 * the desktop server and, since the P2P feature, other foregrounded phones
 * (distinguished by the TXT record `role`).
 *
 * Only ever collected **while the onboarding screen is open** (or as a one-shot to
 * refresh a stale address / find peers for a sync round) — never as a background
 * service, so it costs no battery when idle. NSD does NOT work on the standard
 * emulator (isolated NAT); use the manual-address fallback there.
 */
@Singleton
class ServerDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** The service name this device itself registered (set by PeerAdvertiser, using the
     *  possibly-renamed name NSD confirms). Filtered out of results so a phone never
     *  discovers — or tries to sync with — itself. */
    @Volatile
    var ownServiceName: String? = null

    /** Emits the current set of discovered endpoints, updated as they come and go. */
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
            if (info.serviceName == ownServiceName) return
            val host = info.hostAddressString() ?: return
            val entry = DiscoveredServer(info.serviceName, host, info.port, info.role())
            synchronized(found) { found[info.serviceName] = entry }
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
     * One-shot: the first *server* (never a peer phone) seen within [timeoutMs], else
     * null. Used to refresh a stale stored address (e.g. the server got a new DHCP IP)
     * when a sync fails — a peer must not end up stored as the server URL. Returns
     * null quickly when off-LAN or the server is down, so it's cheap.
     */
    suspend fun discoverFirst(timeoutMs: Long = 4_000): DiscoveredServer? =
        withTimeoutOrNull(timeoutMs) {
            discover()
                .map { list -> list.filter { it.role == DiscoveredRole.SERVER } }
                .firstOrNull { it.isNotEmpty() }
                ?.firstOrNull()
        }

    /**
     * Snapshot of the peer phones visible right now: browse for [timeoutMs] and return
     * everything that answered with `role=peer`. Used by SyncEngine as the fallback
     * target list when the server is unreachable. Bounded and best-effort — an empty
     * list just means "no peer awake in this Wi-Fi at the moment".
     */
    suspend fun discoverPeers(timeoutMs: Long = 3_000): List<DiscoveredServer> {
        // Short-circuit on the first non-empty peer set instead of always sitting out
        // the full window — one reachable peer is enough for a converging exchange,
        // and this keeps the sync round snappy while the server is off.
        return withTimeoutOrNull(timeoutMs) {
            discover()
                .map { list -> list.filter { it.role == DiscoveredRole.PEER } }
                .firstOrNull { it.isNotEmpty() }
        } ?: emptyList()
    }

    private companion object {
        const val SERVICE_TYPE = "_opencook._tcp."
    }
}

/** TXT `role` attribute → [DiscoveredRole]; absent/unknown means an (older) server. */
private fun NsdServiceInfo.role(): DiscoveredRole =
    if (attributes["role"]?.toString(Charsets.UTF_8) == "peer") DiscoveredRole.PEER else DiscoveredRole.SERVER

@Suppress("DEPRECATION")
private fun NsdServiceInfo.hostAddressString(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        hostAddresses.firstOrNull()?.hostAddress
    } else {
        host?.hostAddress
    }
