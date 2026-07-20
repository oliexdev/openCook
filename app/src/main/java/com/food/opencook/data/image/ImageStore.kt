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

package com.food.opencook.data.image

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns local image files. Captured photos and gallery imports are copied into the
 * app cache so we hold a stable, owned file (independent of the source content
 * URI's lifetime) to upload and to keep as the recipe's source photo.
 */
@Singleton
class ImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val capturesDir: File
        get() = File(context.cacheDir, "captures").apply { mkdirs() }

    /** Persistent cache of images downloaded from the server (synced from other
     *  devices). Kept in filesDir, not cacheDir, so Android can't reclaim it under
     *  pressure — that would re-break the offline-first guarantee for synced photos. */
    private val downloadsDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    /** Persistent home for on-device edits (crop/rotate) that haven't been uploaded
     *  yet. In filesDir, not cacheDir, so Android can't reclaim the *only* copy of a
     *  fresh crop before the next sync uploads it (the server is often offline). */
    private val editsDir: File
        get() = File(context.filesDir, "edits").apply { mkdirs() }

    /** A fresh destination file for a CameraX capture. */
    fun newCaptureFile(): File = File(capturesDir, "${UUID.randomUUID()}.jpg")

    /** A fresh destination file for a local edit (crop/rotate) pending upload. */
    fun newEditFile(): File = File(editsDir, "${UUID.randomUUID()}.jpg")

    /** Copy a picked gallery image into the cache; returns the absolute path. */
    suspend fun saveFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val dest = newCaptureFile()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open $uri")
        input.use { source -> dest.outputStream().use { source.copyTo(it) } }
        dest.absolutePath
    }

    /** Persist raw image bytes (e.g. a decoded data-URI or a zip entry) into the cache;
     *  returns the absolute path. */
    suspend fun saveBytes(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val dest = newCaptureFile()
        dest.outputStream().use { it.write(bytes) }
        dest.absolutePath
    }

    /**
     * Persist a photo restored from a backup archive, streaming so a large library never
     * lands in memory. Lands in [editsDir] because a restored photo is in exactly that
     * state: a durable local file with no server name yet, which the next sync uploads
     * (minting the real content-addressed name and the `imageRef` message).
     */
    suspend fun saveRestoredImage(source: InputStream): String = withContext(Dispatchers.IO) {
        val dest = newEditFile()
        dest.outputStream().use { source.copyTo(it) }
        dest.absolutePath
    }

    /** Persist a downloaded server image keyed by its server filename (already content-
     *  addressed — sha256.jpg — so the name is collision-free). Returns the absolute
     *  path to store as the image row's localPath. */
    suspend fun saveDownloadedImage(serverName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val dest = File(downloadsDir, serverName)
        dest.outputStream().use { it.write(bytes) }
        dest.absolutePath
    }

    /** Absolute path of an already-stored download for this content-addressed name, or
     *  null. Lets sync skip re-downloading bytes a peer already pushed to us. */
    suspend fun existingDownload(serverName: String): String? = withContext(Dispatchers.IO) {
        // Reject any name that isn't a plain filename — [serverName] reaches the peer
        // HTTP endpoint from the network, and this guard keeps traversal out of filesDir.
        if (serverName.contains('/') || serverName.contains('\\') || serverName == "..") return@withContext null
        File(downloadsDir, serverName).takeIf { it.isFile }?.absolutePath
    }

    /** Wipe every image file this device owns (captures and downloads). Used when
     *  leaving the household so household photos don't linger on disk. */
    suspend fun wipeAll(): Unit = withContext(Dispatchers.IO) {
        capturesDir.listFiles()?.forEach { it.delete() }
        downloadsDir.listFiles()?.forEach { it.delete() }
        editsDir.listFiles()?.forEach { it.delete() }
    }
}
