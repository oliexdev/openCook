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

package com.food.opencook.data.backup

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Why an archive was rejected — mapped to a user-facing message by the UI. */
sealed interface BackupRejected {
    /** Not a zip, or none of the expected entries were found. */
    data object NotABackup : BackupRejected

    /** Written by a newer openCook than this one. */
    data class TooNew(val formatVersion: Int) : BackupRejected

    /** An entry name or size looked hostile (path traversal, zip bomb). */
    data class Unsafe(val detail: String) : BackupRejected
}

class BackupRejectedException(val reason: BackupRejected) :
    Exception("Backup rejected: $reason")

/**
 * Reads a [BackupFormat] archive. Split from the importer so the UI can show a
 * confirmation dialog ("backup from 19.07., 412 recipes — restore?") before anything
 * touches the database: [readManifest] consumes only the manifest entry and stops.
 *
 * The archive comes from outside the app — the user picks any file they like — so every
 * entry name is checked against [BackupFormat.isAllowedEntry] and both per-entry and
 * total decompressed size are capped before the bytes reach [ImageStore], which does not
 * validate the names it is given.
 */
class BackupReader(private val json: Json) {

    /**
     * Read just `manifest.json`. Reads at most [BackupFormat.MAX_ENTRY_BYTES] and returns
     * as soon as the manifest is found, so this stays cheap on a multi-hundred-MB archive.
     *
     * @throws BackupRejectedException if the file is not a readable openCook backup.
     */
    fun readManifest(input: InputStream): BackupManifest {
        val manifest = runCatching {
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { !it.isDirectory && it.name == BackupFormat.MANIFEST }
                    ?.let { json.decodeFromString(BackupManifest.serializer(), zip.readCapped().decodeToString()) }
            }
        }.getOrNull() ?: throw BackupRejectedException(BackupRejected.NotABackup)

        if (manifest.formatVersion > BackupFormat.VERSION) {
            throw BackupRejectedException(BackupRejected.TooNew(manifest.formatVersion))
        }
        return manifest
    }

    /**
     * Walk the archive once, handing every allowed entry to [onEntry] as a stream that
     * must be consumed before the next callback. Unknown entries are skipped silently
     * (forward compatibility with a future minor addition); hostile ones abort.
     */
    suspend fun forEachEntry(input: InputStream, onEntry: suspend (name: String, stream: InputStream) -> Unit) {
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (!BackupFormat.isAllowedEntry(name)) {
                    // A traversal attempt is hostile; anything else is just unknown.
                    if (name.contains("..") || name.contains('\\') || name.startsWith("/")) {
                        throw BackupRejectedException(BackupRejected.Unsafe("entry name: $name"))
                    }
                    continue
                }
                val counting = CountingInputStream(zip)
                onEntry(name, counting)
                total += counting.count
                if (counting.count > BackupFormat.MAX_ENTRY_BYTES) {
                    throw BackupRejectedException(BackupRejected.Unsafe("entry too large: $name"))
                }
                if (total > BackupFormat.MAX_TOTAL_BYTES) {
                    throw BackupRejectedException(BackupRejected.Unsafe("archive too large"))
                }
            }
        }
    }

    private fun InputStream.readCapped(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > BackupFormat.MAX_ENTRY_BYTES) {
                throw BackupRejectedException(BackupRejected.Unsafe("entry too large"))
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

/** Counts what was actually read so the caller can enforce a decompressed-size cap
 *  without buffering the entry. Deliberately does not close the underlying stream —
 *  it is the shared [ZipInputStream]. */
private class CountingInputStream(private val delegate: InputStream) : InputStream() {
    var count = 0L
        private set

    override fun read(): Int = delegate.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        delegate.read(b, off, len).also { if (it > 0) count += it }

    override fun available(): Int = delegate.available()

    override fun close() = Unit
}
