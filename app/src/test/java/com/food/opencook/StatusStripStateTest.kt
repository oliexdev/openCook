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

import com.food.opencook.data.local.entity.JobEntity
import com.food.opencook.ui.status.StripMode
import com.food.opencook.ui.status.statusStripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusStripStateTest {

    private fun job(id: String, status: String, stage: String? = null, created: Long = 0) =
        JobEntity(jobId = id, localImagePath = "/x", status = status, stage = stage, createdAt = created, updatedAt = 0)

    @Test
    fun hiddenWhenNothing() {
        val s = statusStripState(emptyList(), emptyList(), emptyList())
        assertEquals(StripMode.HIDDEN, s.mode)
    }

    @Test
    fun singleProcessingShowsStage() {
        val s = statusStripState(listOf(job("a", "processing", "reading_text")), emptyList(), emptyList())
        assertEquals(StripMode.ACTIVE, s.mode)
        assertEquals(1, s.processingCount)
        assertEquals(0, s.queuedCount)
        assertEquals("reading_text", s.oldestStageKey)
    }

    @Test
    fun splitsProcessingAndQueued() {
        val s = statusStripState(
            listOf(
                job("old", "processing", "detecting_photos", created = 1),
                job("new", "pending", created = 2),
            ),
            emptyList(),
            emptyList(),
        )
        assertEquals(1, s.processingCount)
        assertEquals(1, s.queuedCount)
        assertEquals("detecting_photos", s.oldestStageKey) // oldest processing
    }

    @Test
    fun queuedOnlyHasNoStage() {
        val s = statusStripState(listOf(job("a", "pending")), emptyList(), emptyList())
        assertEquals(0, s.processingCount)
        assertEquals(1, s.queuedCount)
        assertNull(s.oldestStageKey)
    }

    @Test
    fun activeWinsOverFailedAndFinished() {
        val s = statusStripState(
            listOf(job("a", "processing", "reading_text")),
            listOf(job("b", "error")),
            listOf("X"),
        )
        assertEquals(StripMode.ACTIVE, s.mode)
    }

    @Test
    fun failedWinsOverFinishedWhenNoActive() {
        val s = statusStripState(emptyList(), listOf(job("b", "error")), listOf("X"))
        assertEquals(StripMode.FAILED, s.mode)
        assertEquals(1, s.failedCount)
    }

    @Test
    fun finishedShowsNames() {
        val s = statusStripState(emptyList(), emptyList(), listOf("Curry-Nudeln", "Suppe"))
        assertEquals(StripMode.FINISHED, s.mode)
        assertEquals(listOf("Curry-Nudeln", "Suppe"), s.finishedRecipeNames)
    }
}
