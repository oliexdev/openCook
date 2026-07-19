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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.food.opencook.MainActivity
import com.food.opencook.R
import com.food.opencook.sync.SyncTrigger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The visible half of the phone-to-phone sync switch: a minimal foreground service
 * that keeps this phone answerable for the household while the app is closed. It
 * owns no logic of its own — it just holds the [PeerAdvertiser]'s standby signal
 * (which brings the embedded server + mDNS advertisement up, Wi-Fi-gated as always)
 * and fires one catch-up sync whenever a Wi-Fi network appears, so a phone that
 * was away picks up the family's changes right when it comes home.
 *
 * Android requires the permanent notification for any long-lived background
 * presence; the channel is IMPORTANCE_MIN so it stays silent and collapsed.
 * Declared as `specialUse` FGS type: `dataSync` gets a runtime cap (6 h) on
 * Android 15+, which would silently kill the responder role.
 */
@AndroidEntryPoint
class PeerStandbyService : Service() {

    @Inject lateinit var peerAdvertiser: PeerAdvertiser
    @Inject lateinit var syncTrigger: SyncTrigger

    private var wifiCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val startType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), startType)
        peerAdvertiser.setStandby(true)

        // "Coming home" catch-up: joining a Wi-Fi network triggers one sync round so
        // changes made while this phone was away arrive without opening the app.
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                syncTrigger.requestSync()
            }
        }
        wifiCallback = callback
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onFailure { Log.w(TAG, "standby wifi callback registration failed", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        wifiCallback?.let { cb ->
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
        wifiCallback = null
        peerAdvertiser.setStandby(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_STANDBY,
            getString(R.string.standby_channel_name),
            NotificationManager.IMPORTANCE_MIN, // silent, collapsed, no status-bar icon
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_STANDBY)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle(getString(R.string.standby_notification_title))
            .setContentText(getString(R.string.standby_notification_text))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    companion object {
        private const val TAG = "OpenCookP2P"
        private const val CHANNEL_STANDBY = "standby"
        private const val NOTIFICATION_ID = 4210

        /**
         * Bring the service in line with the desired state. Best-effort: starting a
         * foreground service is only allowed while the app is (about to be) visible —
         * a background-started process (e.g. WorkManager) simply skips and the next
         * app open re-runs this (the advertiser's controller includes the foreground
         * signal in its trigger exactly for that retry).
         */
        fun ensure(context: Context, shouldRun: Boolean) {
            val intent = Intent(context, PeerStandbyService::class.java)
            if (shouldRun) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { Log.w(TAG, "standby service start not allowed right now", it) }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}
