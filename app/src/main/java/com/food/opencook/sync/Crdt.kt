package com.food.opencook.sync

/**
 * The materialised state the merge writes into, plus the per-field clock that
 * records which message currently "owns" each field. Backed by Room in the app
 * and by SQLite on the server; an in-memory map in tests.
 */
interface MaterializedStore {
    /** Packed HLC of the value currently applied to this field, or null if none. */
    fun fieldClock(dataset: String, rowId: String, column: String): String?

    /** Set the field's value and record [timestamp] as its new owning clock. */
    fun applyField(dataset: String, rowId: String, column: String, value: String, timestamp: String)
}

/**
 * Conflict-free merge: per-column last-write-wins keyed by HLC. A message is
 * applied only if it is strictly newer than the field's current clock, so
 * applying the same set of messages in ANY order converges to the same state
 * (and whole records are never silently lost — losers are resolved per field).
 */
object Crdt {

    /** Apply one message. Returns true if it won (changed state), false if stale. */
    fun apply(store: MaterializedStore, message: Message): Boolean {
        val current = store.fieldClock(message.dataset, message.rowId, message.column)
        if (current != null && message.timestamp <= current) return false
        store.applyField(message.dataset, message.rowId, message.column, message.value, message.timestamp)
        return true
    }

    fun applyAll(store: MaterializedStore, messages: Iterable<Message>) {
        for (message in messages) apply(store, message)
    }
}
