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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the rest of the app whether this phone is on a network that can *possibly*
 * reach the household at all — the home server and peer phones live on the LAN, so
 * mobile data is not "a slow connection", it is no connection.
 *
 * Without this, sync ran its full routine every 30 s on cellular: a connect attempt
 * against a stored `192.168.x.x` address (which on mobile data can only time out —
 * or, worse, land on some stranger's device inside the carrier network), then two
 * mDNS discovery rounds that can't see anything either. Expensive, pointless, and
 * the UI could only report a misleading "server unreachable".
 *
 * VPN counts as home: reaching the server through a tunnel from outside is an
 * explicitly supported setup, and such a network carries `TRANSPORT_VPN`.
 */
@Singleton
class LanMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Wi-Fi or Ethernet — the transports on which *this* phone can be found by others.
     *  Used by the peer responder: announcing over a VPN tunnel would reach nobody. */
    fun onLocalWifi(): Flow<Boolean> =
        transportsAvailable(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_ETHERNET)

    /** Wi-Fi, Ethernet or VPN — the transports on which the household *may* be reachable. */
    fun onHomeNetwork(): Flow<Boolean> =
        transportsAvailable(
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_VPN,
        )

    /** One-shot answer for callers that can't collect a flow (workers, one-off checks). */
    fun isOnHomeNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // can't tell → never block sync on a guess
        return cm.matching(
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_VPN,
        ).isNotEmpty()
    }

    /**
     * Emits whether at least one network with any of [transports] is up, live-updated.
     *
     * Deliberately *not* the default network: a home Wi-Fi without internet access makes
     * Android keep cellular as the default route while the Wi-Fi link — and with it the
     * whole LAN — stays perfectly usable. Any matching network therefore counts.
     */
    private fun transportsAvailable(vararg transports: Int): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        // Callbacks report *changes*; seed with what is already connected so the UI
        // doesn't flash "not at home" for a beat on every start.
        val live = Collections.synchronizedSet(cm.matching(*transports).toMutableSet())
        trySend(live.isNotEmpty())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                live.add(network)
                trySend(live.isNotEmpty())
            }

            override fun onLost(network: Network) {
                live.remove(network)
                trySend(live.isNotEmpty())
            }
        }
        val request = NetworkRequest.Builder()
            // The builder requires NOT_VPN by default, which would hide a tunnel from us.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .apply { transports.forEach(::addTransportType) }
            .build()
        val registered = runCatching { cm.registerNetworkCallback(request, callback) }.isSuccess
        if (!registered) trySend(true) // can't observe → assume reachable, behave as before
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged().conflate()
}

/** The currently connected networks carrying any of [transports]. */
@Suppress("DEPRECATION") // allNetworks: no non-deprecated way to enumerate synchronously
private fun ConnectivityManager.matching(vararg transports: Int): List<Network> =
    runCatching { allNetworks.toList() }.getOrDefault(emptyList()).filter { network ->
        val caps = runCatching { getNetworkCapabilities(network) }.getOrNull()
        caps != null && transports.any { caps.hasTransport(it) }
    }
