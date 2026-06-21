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

/**
 * A Merkle trie over message timestamps, bucketed by the minute of their physical
 * time (base-3 digits). Each node's [hash] is the XOR of the FNV-1a hashes of all
 * timestamps in its subtree, so two peers can compare roots and, when they differ,
 * descend to find the earliest minute at which their logs diverge — then exchange
 * only messages from that point on. Mirrors server/app/sync.py; both must agree,
 * so the algorithm is verified against shared vectors.
 *
 * Reference: "CRDTs for Mortals" / ActualBudget.
 */
class Merkle {
    var hash: Int = 0
    val children: HashMap<Char, Merkle> = HashMap()
}

object MerkleTrie {
    /** Fixed base-3 key width: covers minute-since-epoch well past the next century. */
    const val KEY_LEN = 18
    private const val MINUTE_MS = 60_000L

    fun build(packedTimestamps: Iterable<String>): Merkle {
        val root = Merkle()
        packedTimestamps.forEach { insert(root, it) }
        return root
    }

    fun insert(root: Merkle, packedTimestamp: String) {
        val h = fnv1a32(packedTimestamp)
        val key = keyOf(Hlc.parse(packedTimestamp).millis)
        root.hash = root.hash xor h
        var node = root
        for (c in key) {
            node = node.children.getOrPut(c) { Merkle() }
            node.hash = node.hash xor h
        }
    }

    /** Earliest divergent time (epoch millis, minute-floor) or null if the tries match. */
    fun diff(a: Merkle, b: Merkle): Long? {
        if (a.hash == b.hash) return null
        var na = a
        var nb = b
        val prefix = StringBuilder()
        while (true) {
            val keys = (na.children.keys + nb.children.keys).toSortedSet()
            val diffKey = keys.firstOrNull { k ->
                (na.children[k]?.hash ?: 0) != (nb.children[k]?.hash ?: 0)
            } ?: return prefixToMillis(prefix.toString())
            prefix.append(diffKey)
            na = na.children[diffKey] ?: Merkle()
            nb = nb.children[diffKey] ?: Merkle()
        }
    }

    private fun keyOf(millis: Long): String =
        (millis / MINUTE_MS).toString(3).padStart(KEY_LEN, '0')

    private fun prefixToMillis(prefix: String): Long {
        if (prefix.isEmpty()) return 0L
        val minute = prefix.padEnd(KEY_LEN, '0').toLong(3)
        return minute * MINUTE_MS
    }
}

/** 32-bit FNV-1a; identical bit pattern in Kotlin (Int) and Python (masked). */
fun fnv1a32(s: String): Int {
    var h = 0x811c9dc5.toInt()
    for (c in s) {
        h = h xor c.code
        h *= 0x01000193
    }
    return h
}

/** The trie's root hash as an unsigned 32-bit value (for transport / cross-language compare). */
fun Merkle.unsignedHash(): Long = hash.toLong() and 0xFFFFFFFFL
