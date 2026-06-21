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

package com.food.opencook.sync

import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.entity.MessageEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stamps a batch of field changes with fresh HLCs, appends them to the message
 * log, and asks for a sync. Shared by every repository that mutates synced data,
 * so the "write materialised + append message + sync" rule lives in one place.
 */
@Singleton
class MessageRecorder @Inject constructor(
    private val messageDao: MessageDao,
    private val stamper: Stamper,
    private val syncTrigger: SyncTrigger,
) {
    suspend fun record(changes: List<FieldChange>) {
        if (changes.isEmpty()) return
        val now = System.currentTimeMillis()
        val entities = changes.map { change ->
            MessageEntity(
                timestamp = stamper.stamp().pack(),
                dataset = change.dataset,
                rowId = change.rowId,
                column = change.column,
                value = change.value,
                createdAt = now,
            )
        }
        messageDao.insertAll(entities)
        syncTrigger.requestSync()
    }
}
