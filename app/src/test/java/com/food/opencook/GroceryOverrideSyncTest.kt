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

import com.food.opencook.data.local.entity.GroceryOverrideEntity
import com.food.opencook.sync.GroceryOverrideMessageEncoder
import com.food.opencook.sync.SyncDatasets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learned "name → aisle" lesson travels the log keyed by the normalized name, so
 * per-name LWW resolves concurrent corrections and a re-drag simply overrides.
 */
class GroceryOverrideSyncTest {

    @Test
    fun encodesLessonKeyedByName() {
        val changes = GroceryOverrideMessageEncoder.encode(
            GroceryOverrideEntity(name = "kokosmilch", category = "DRINKS", updatedAt = 0),
        )
        // rowId is the normalized name — the natural key that makes LWW per-lesson.
        assertTrue(changes.all { it.dataset == SyncDatasets.GROCERY_OVERRIDES && it.rowId == "kokosmilch" })
        assertEquals("\"DRINKS\"", changes.first { it.column == "category" }.value)
        assertEquals("false", changes.first { it.column == SyncDatasets.COLUMN_DELETED }.value)
    }

    @Test
    fun tombstoneForgetsALesson() {
        val changes = GroceryOverrideMessageEncoder.tombstone("kokosmilch")
        assertEquals(1, changes.size)
        assertEquals(SyncDatasets.GROCERY_OVERRIDES, changes[0].dataset)
        assertEquals("kokosmilch", changes[0].rowId)
        assertEquals(SyncDatasets.COLUMN_DELETED, changes[0].column)
        assertEquals("true", changes[0].value)
    }
}
