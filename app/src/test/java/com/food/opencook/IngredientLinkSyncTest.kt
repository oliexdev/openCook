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

import com.food.opencook.data.local.entity.IngredientLinkEntity
import com.food.opencook.sync.IngredientLinkMessageEncoder
import com.food.opencook.sync.SyncDatasets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A learned "not the same product" distinction travels the log carrying its two names as
 * fields (the applier decodes those, never the Unit-Separator-joined rowId), so per-pair LWW
 * resolves concurrent lessons and a re-teach simply overrides.
 */
class IngredientLinkSyncTest {

    @Test
    fun encodesDistinctionWithNamesAsFields() {
        val changes = IngredientLinkMessageEncoder.encode(
            IngredientLinkEntity(nameA = "bohnen", nameB = "weiße bohnen", kind = "distinct", updatedAt = 0),
        )
        // rowId joins the pair with a Unit Separator (collision-free, never parsed).
        assertTrue(changes.all { it.dataset == SyncDatasets.INGREDIENT_LINKS && it.rowId == "bohnenweiße bohnen" })
        assertEquals("\"bohnen\"", changes.first { it.column == "nameA" }.value)
        assertEquals("\"weiße bohnen\"", changes.first { it.column == "nameB" }.value)
        assertEquals("\"distinct\"", changes.first { it.column == "kind" }.value)
        assertEquals("false", changes.first { it.column == SyncDatasets.COLUMN_DELETED }.value)
    }

    @Test
    fun tombstoneCarriesNamesForDeleteByPair() {
        val changes = IngredientLinkMessageEncoder.tombstone("bohnen", "weiße bohnen")
        // The names ride along so the applier can delete by pair (the rowId is not parsed).
        assertEquals("\"bohnen\"", changes.first { it.column == "nameA" }.value)
        assertEquals("\"weiße bohnen\"", changes.first { it.column == "nameB" }.value)
        assertEquals("true", changes.first { it.column == SyncDatasets.COLUMN_DELETED }.value)
    }
}
