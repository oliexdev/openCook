package com.food.opencook.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

        runCatching {
            NotificationManagerCompat.from(context).notify(localJobId.hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL_JOBS = "jobs"
        const val DEEP_LINK_REVIEW = "opencook://review"
    }
}

private fun String.toUri(): Uri = Uri.parse(this)
