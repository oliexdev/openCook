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

package com.food.opencook

import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.sync.Hlc
import com.food.opencook.sync.MerkleTrie
import com.food.opencook.sync.SyncResponder
import com.food.opencook.sync.toDto
import com.food.opencook.sync.toMerkle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone-to-phone exchange protocol: the responder's selection rule
 * ([SyncResponder.missingSince]) and the Merkle wire round-trip, exercised the way
 * two phones use them. The full HTTP path is covered by the two-device click test;
 * this pins the protocol semantics (mirroring server/app/api/sync.py).
 */
class PeerSyncExchangeTest {

    private fun msg(minute: Long, counter: Int, node: String, value: String = "\"v\"") =
        MessageEntity(
            timestamp = Hlc(minute * 60_000, counter, node).pack(),
            dataset = "recipes",
            rowId = "r-$node-$minute-$counter",
            column = "name",
            value = value,
            createdAt = 0L,
        )

    private fun trie(log: List<MessageEntity>) = MerkleTrie.build(log.map { it.timestamp })

    @Test
    fun merkleDtoRoundTripIsLossless() {
        val log = listOf(msg(100, 0, "A"), msg(100, 1, "A"), msg(2_000_000, 0, "B"))
        val original = trie(log)
        val roundTripped = original.toDto().toMerkle()
        // A lossless round trip means the tries don't diverge anywhere.
        assertNull(MerkleTrie.diff(original, roundTripped))
    }

    @Test
    fun caughtUpRequesterGetsNothing() {
        val log = listOf(msg(100, 0, "A"), msg(101, 0, "B"))
        assertEquals(emptyList<MessageEntity>(), SyncResponder.missingSince(log, trie(log), trie(log).toDto()))
    }

    @Test
    fun requesterBehindGetsEverythingFromTheDivergentMinuteOn() {
        val shared = listOf(msg(100, 0, "A"), msg(101, 0, "A"))
        val newer = listOf(msg(102, 0, "A"), msg(103, 0, "A"))
        val responderLog = shared + newer
        val requesterTrie = trie(shared)

        val missing = SyncResponder.missingSince(responderLog, trie(responderLog), requesterTrie.toDto())
        // Divergence starts at minute 102 — the shared prefix must not be resent.
        assertEquals(newer.map { it.timestamp }.toSet(), missing.map { it.timestamp }.toSet())
    }

    @Test
    fun divergenceInsideAMinuteResendsThatWholeMinute() {
        val shared = msg(100, 0, "A")
        val onlyResponder = msg(100, 1, "B") // same minute, different message
        val responderLog = listOf(shared, onlyResponder)
        val requesterTrie = trie(listOf(shared))

        val missing = SyncResponder.missingSince(responderLog, trie(responderLog), requesterTrie.toDto())
        // Minute-bucketed diff can only point at the minute, so both its messages come
        // back; the requester's idempotent log drops the duplicate.
        assertEquals(responderLog.map { it.timestamp }.toSet(), missing.map { it.timestamp }.toSet())
    }

    /** One push+pull round in each direction, exactly like SyncEngine ↔ SyncResponder. */
    private fun exchange(initiator: MutableList<MessageEntity>, responder: MutableList<MessageEntity>) {
        val requestMerkle = trie(initiator).toDto()
        // Responder ingests the pushed log (idempotent by timestamp)...
        val known = responder.map { it.timestamp }.toSet()
        responder += initiator.filter { it.timestamp !in known }
        // ...then answers with what the initiator is missing.
        val response = SyncResponder.missingSince(responder, trie(responder), requestMerkle)
        val initiatorKnown = initiator.map { it.timestamp }.toSet()
        initiator += response.filter { it.timestamp !in initiatorKnown }
    }

    @Test
    fun twoPhonesConvergeInOneRoundAndStayQuiet() {
        val a = mutableListOf(msg(100, 0, "A"), msg(105, 0, "A"))
        val b = mutableListOf(msg(101, 0, "B"), msg(103, 0, "B"))

        exchange(a, b)
        assertEquals(a.map { it.timestamp }.toSet(), b.map { it.timestamp }.toSet())
        assertEquals(4, a.size)

        // Caught up: another round moves nothing and the tries agree.
        exchange(a, b)
        assertEquals(4, a.size)
        assertEquals(4, b.size)
        assertNull(MerkleTrie.diff(trie(a), trie(b)))
    }

    @Test
    fun threePhonesConvergeTransitively() {
        // A never talks to C — B relays by construction of the log exchange.
        val a = mutableListOf(msg(100, 0, "A"))
        val b = mutableListOf(msg(101, 0, "B"))
        val c = mutableListOf(msg(102, 0, "C"))

        exchange(a, b)
        exchange(b, c)
        exchange(a, b)

        val all = setOf(a, b, c).flatten().map { it.timestamp }.toSet()
        assertEquals(3, all.size)
        listOf(a, b, c).forEach { log ->
            assertTrue(log.map { it.timestamp }.toSet() == all)
        }
    }
}
