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
 * A Hybrid Logical Clock timestamp: physical [millis] + a logical [counter] to
 * order events within the same millisecond, plus the originating [node] as a
 * deterministic tiebreaker.
 *
 * Serialised to a fixed-width, lexicographically-sortable string so string
 * comparison equals causal/time order — and so the Kotlin and Python
 * implementations produce byte-identical timestamps (shared test vectors).
 * Layout: `<15-digit millis>-<4-hex counter>-<node>`.
 */
data class Hlc(val millis: Long, val counter: Int, val node: String) : Comparable<Hlc> {

    fun pack(): String = buildString {
        append(millis.toString().padStart(MILLIS_WIDTH, '0'))
        append('-')
        append(counter.toString(16).uppercase().padStart(COUNTER_WIDTH, '0'))
        append('-')
        append(node)
    }

    override fun compareTo(other: Hlc): Int = pack().compareTo(other.pack())

    override fun toString(): String = pack()

    companion object {
        const val MILLIS_WIDTH = 15
        const val COUNTER_WIDTH = 4
        const val MAX_COUNTER = 0xFFFF

        fun parse(packed: String): Hlc {
            val first = packed.indexOf('-')
            val second = packed.indexOf('-', first + 1)
            require(first > 0 && second > first) { "Invalid HLC: $packed" }
            return Hlc(
                millis = packed.substring(0, first).toLong(),
                counter = packed.substring(first + 1, second).toInt(16),
                node = packed.substring(second + 1),
            )
        }
    }
}
