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

import com.food.opencook.sync.ClockDriftException
import com.food.opencook.sync.Hlc
import com.food.opencook.sync.HlcClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HlcTest {

    @Test
    fun packIsFixedWidthAndSortable() {
        val a = Hlc(1000, 1, "node").pack()
        val b = Hlc(1000, 2, "node").pack()
        val c = Hlc(1001, 0, "node").pack()
        assertTrue(a < b)
        assertTrue(b < c)
        // 15-digit millis + '-' + 4-hex counter + '-' + node
        assertEquals("000000000001000-0001-node", a)
    }

    @Test
    fun parseRoundTrips() {
        val hlc = Hlc(1_700_000_000_000, 255, "abc-def") // node may contain '-'
        assertEquals(hlc, Hlc.parse(hlc.pack()))
    }

    @Test
    fun sendIncrementsCounterWithinSameMillisAndResetsAcross() {
        val clock = HlcClock("A")
        val t1 = clock.send(1000)
        val t2 = clock.send(1000)
        val t3 = clock.send(1001)
        assertEquals(0, t1.counter)
        assertEquals(1, t2.counter)
        assertEquals(0, t3.counter)
    }

    @Test
    fun sendNeverGoesBackwardsIfWallClockDoes() {
        val clock = HlcClock("A")
        clock.send(5000)
        val t = clock.send(4000) // wall clock jumped back
        assertEquals(5000, t.millis)
        assertEquals(1, t.counter)
    }

    @Test
    fun recvAdvancesPastRemote() {
        val clock = HlcClock("A")
        val remote = Hlc(9000, 3, "B")
        val t = clock.recv(remote, now = 1000)
        assertTrue("local must be strictly after remote", remote < t)
        assertEquals(9000, t.millis)
        assertEquals(4, t.counter)
        // a subsequent local send stays ahead of the remote
        assertTrue(remote < clock.send(1000))
    }

    @Test
    fun counterOverflowThrows() {
        val clock = HlcClock("A")
        assertThrows(ClockDriftException::class.java) {
            repeat(Hlc.MAX_COUNTER + 2) { clock.send(1000) } // all same millis
        }
    }
}
