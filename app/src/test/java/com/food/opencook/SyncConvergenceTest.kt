package com.food.opencook

import com.food.opencook.sync.Crdt
import com.food.opencook.sync.HlcClock
import com.food.opencook.sync.MaterializedStore
import com.food.opencook.sync.Message
import com.food.opencook.sync.SyncDatasets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** In-memory materialised store: (dataset,row,column) -> (value, owning HLC). */
private class InMemoryStore : MaterializedStore {
    private val fields = HashMap<Triple<String, String, String>, Pair<String, String>>()
    override fun fieldClock(dataset: String, rowId: String, column: String): String? =
        fields[Triple(dataset, rowId, column)]?.second
    override fun applyField(dataset: String, rowId: String, column: String, value: String, timestamp: String) {
        fields[Triple(dataset, rowId, column)] = value to timestamp
    }
    fun value(dataset: String, rowId: String, column: String): String? =
        fields[Triple(dataset, rowId, column)]?.first
    /** Values only (the converged materialised state, independent of clocks). */
    fun snapshot(): Map<Triple<String, String, String>, String> = fields.mapValues { it.value.first }
}

class SyncConvergenceTest {

    private val R = SyncDatasets.RECIPES

    private fun msg(clock: HlcClock, now: Long, row: String, col: String, value: String) =
        Message(clock.send(now).pack(), R, row, col, value)

    /** Two devices edit overlapping and disjoint fields; build a fixed message set. */
    private fun sampleMessages(): List<Message> {
        val a = HlcClock("A")
        val b = HlcClock("B")
        return listOf(
            msg(a, 1000, "r1", "name", "\"A's name\""),
            msg(b, 1000, "r1", "name", "\"B's name\""),     // concurrent edit of same field
            msg(a, 1001, "r1", "servings", "\"2\""),        // disjoint field
            msg(b, 1002, "r1", "name", "\"B's newer name\""), // newest for r1.name
            msg(a, 1002, "r2", "name", "\"Second recipe\""), // different row
            msg(b, 1003, "r1", SyncDatasets.COLUMN_DELETED, "false"),
        )
    }

    @Test
    fun convergesRegardlessOfOrder() {
        val messages = sampleMessages()
        val inOrder = InMemoryStore().apply { Crdt.applyAll(this, messages) }
        val reversed = InMemoryStore().apply { Crdt.applyAll(this, messages.reversed()) }
        val shuffled = InMemoryStore().apply { Crdt.applyAll(this, messages.shuffled(Random(42))) }
        val shuffled2 = InMemoryStore().apply { Crdt.applyAll(this, messages.shuffled(Random(7))) }

        assertEquals(inOrder.snapshot(), reversed.snapshot())
        assertEquals(inOrder.snapshot(), shuffled.snapshot())
        assertEquals(inOrder.snapshot(), shuffled2.snapshot())
    }

    @Test
    fun perFieldLwwPicksHighestHlcAndKeepsDisjointFields() {
        val store = InMemoryStore().apply { Crdt.applyAll(this, sampleMessages()) }
        // r1.name: newest message wins; r1.servings survives (no whole-record loss).
        assertEquals("\"B's newer name\"", store.value(R, "r1", "name"))
        assertEquals("\"2\"", store.value(R, "r1", "servings"))
        assertEquals("\"Second recipe\"", store.value(R, "r2", "name"))
    }

    @Test
    fun applyingTwiceIsIdempotent() {
        val messages = sampleMessages()
        val once = InMemoryStore().apply { Crdt.applyAll(this, messages) }
        val twice = InMemoryStore().apply {
            Crdt.applyAll(this, messages)
            Crdt.applyAll(this, messages)
        }
        assertEquals(once.snapshot(), twice.snapshot())
    }

    @Test
    fun staleMessageIsRejectedNewerIsAccepted() {
        val a = HlcClock("A")
        val store = InMemoryStore()
        val older = Message(a.send(1000).pack(), R, "r1", "name", "\"old\"")
        val newer = Message(a.send(1001).pack(), R, "r1", "name", "\"new\"")

        assertTrue(Crdt.apply(store, newer))
        assertFalse("older must not overwrite newer", Crdt.apply(store, older))
        assertEquals("\"new\"", store.value(R, "r1", "name"))
    }

    @Test
    fun tombstoneConvergesToDeleted() {
        val a = HlcClock("A")
        val create = Message(a.send(1000).pack(), R, "r1", "name", "\"Soup\"")
        val delete = Message(a.send(1001).pack(), R, "r1", SyncDatasets.COLUMN_DELETED, "true")
        val store = InMemoryStore().apply { Crdt.applyAll(this, listOf(delete, create)) }
        assertEquals("true", store.value(R, "r1", SyncDatasets.COLUMN_DELETED))
    }
}
