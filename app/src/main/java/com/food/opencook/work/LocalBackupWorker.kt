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
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.food.opencook.data.backup.BackupFolder
import com.food.opencook.data.backup.BackupSettings
import com.food.opencook.data.backup.BackupWriter
import com.food.opencook.data.backup.backupFileName
import com.food.opencook.data.local.dao.MessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Writes the periodic automatic backup into the folder the user picked once.
 *
 * Runs while charging so a several-hundred-MB write never surprises anyone mid-day; no
 * network constraint, because this is a purely local file write. Failures are recorded
 * in [BackupSettings] and surfaced in Settings rather than notified — a missed local
 * backup is not urgent enough to earn a notification channel.
 */
@HiltWorker
class LocalBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: BackupSettings,
    private val folder: BackupFolder,
    private val writer: BackupWriter,
    private val messageDao: MessageDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = settings.configOnce()
        val treeUri = config.folderUri?.let(Uri::parse) ?: return Result.success() // switched off

        // Nothing changed since the last run → don't rewrite an identical archive.
        val fingerprint = "${messageDao.count()}:${messageDao.maxTimestamp().orEmpty()}"
        if (fingerprint == config.lastFingerprint) return Result.success()

        val target = folder.createFile(treeUri, backupFileName())
            ?: return fail("Backup folder is not available")

        return try {
            val out = applicationContext.contentResolver.openOutputStream(target)
                ?: return fail("Cannot write to the backup folder")
            out.use { writer.write(it) }
            folder.rotate(treeUri, config.keep)
            settings.recordSuccess(System.currentTimeMillis(), fingerprint)
            Result.success()
        } catch (error: Exception) {
            // A half-written file must never be mistaken for a good backup.
            folder.delete(target)
            fail(error.message ?: error::class.simpleName ?: "Backup failed")
        }
    }

    private suspend fun fail(message: String): Result {
        settings.recordFailure(message)
        // Retry once via WorkManager's backoff; the next periodic run covers us anyway.
        return Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "local-backup"
    }
}
