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

package com.food.opencook.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.food.opencook.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the "recipe recognised" notification that deep-links into the review
 * screen. Used by the background [com.food.opencook.work.PollJobWorker] when the
 * app isn't in the foreground.
 */
@Singleton
class JobNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_JOBS,
            context.getString(R.string.notif_channel_jobs_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_jobs_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Caller is responsible for POST_NOTIFICATIONS; if missing this is a silent no-op. */
    fun notifyRecipeReady(localJobId: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "$DEEP_LINK_REVIEW/$localJobId".toUri(),
        ).apply { setPackage(context.packageName) }

        val pendingIntent = PendingIntent.getActivity(
            context,
            localJobId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_JOBS)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(context.getString(R.string.notif_recipe_ready_title))
            .setContentText(context.getString(R.string.notif_recipe_ready_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Below API 33 notifications need no runtime permission; on 33+ post only when granted.
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canPost) {
            runCatching {
                NotificationManagerCompat.from(context).notify(localJobId.hashCode(), notification)
            }
        }
    }

    companion object {
        const val CHANNEL_JOBS = "jobs"
        const val DEEP_LINK_REVIEW = "opencook://review"
    }
}

private fun String.toUri(): Uri = Uri.parse(this)
