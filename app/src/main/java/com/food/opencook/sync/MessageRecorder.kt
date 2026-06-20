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
