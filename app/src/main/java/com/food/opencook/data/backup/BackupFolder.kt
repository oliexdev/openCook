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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user-picked folder the automatic backup writes into, driven straight through
 * [DocumentsContract].
 *
 * `androidx.documentfile` would do the same job but is not a dependency of this project
 * and pulling one in for ~40 lines of directory listing is a poor trade — especially
 * under the rule that every dependency has to be GPLv3-compatible and permanently free.
 */
@Singleton
class BackupFolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Take long-lived read/write access so the worker can still write months later. */
    fun persistPermission(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    /** Display name of the folder, for the settings row. Null if it is gone. */
    suspend fun displayName(treeUri: Uri): String? = withContext(Dispatchers.IO) {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        runCatching {
            context.contentResolver.query(
                doc,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }

    /** Create a new backup document in the folder; null if the folder is unavailable. */
    suspend fun createFile(treeUri: Uri, name: String): Uri? = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        runCatching {
            DocumentsContract.createDocument(context.contentResolver, parent, BackupFormat.MIME, name)
        }.getOrNull()
    }

    /**
     * Delete all but the newest [keep] openCook backups in the folder. Names embed a
     * sortable timestamp, so lexicographic order is chronological — the same trick the
     * server-side rotation uses.
     */
    suspend fun rotate(treeUri: Uri, keep: Int) = withContext(Dispatchers.IO) {
        val existing = listBackups(treeUri).sortedByDescending { it.first }
        existing.drop(keep).forEach { (_, uri) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }

    /** Best-effort delete — used to clean up a half-written file after a failure. */
    suspend fun delete(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        Unit
    }

    /** (name, uri) of every openCook backup in the folder. */
    private fun listBackups(treeUri: Uri): List<Pair<String, Uri>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val out = mutableListOf<Pair<String, Uri>>()
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith(PREFIX) || !name.endsWith(".zip")) continue
                    out += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
        }
        return out
    }

    private companion object {
        const val PREFIX = "opencook-backup-"
    }
}
