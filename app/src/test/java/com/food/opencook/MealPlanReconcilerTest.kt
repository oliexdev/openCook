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

import com.food.opencook.ui.mealplan.MealPlanReconciler
import com.food.opencook.ui.mealplan.MealPlanReconciler.PastEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealPlanReconcilerTest {

    private val today = LocalDate.of(2026, 6, 13)

    @Test
    fun `procured past dish rolls onto the next free day`() {
        val moves = MealPlanReconciler.reconcile(
            pastUncooked = listOf(PastEntry("a", today.minusDays(1), procured = true)),
            occupied = emptySet(),
            skipped = emptySet(),
            today = today,
        )
        assertEquals(1, moves.size)
        assertEquals("a", moves[0].entryId)
        assertEquals(today, moves[0].toDate) // earliest free slot in [today, today+window]
    }

    @Test
    fun `no free day within window means no move`() {
        // today and the next two days are all occupied or skipped.
        val moves = MealPlanReconciler.reconcile(
            pastUncooked = listOf(PastEntry("a", today.minusDays(1), procured = true)),
            occupied = setOf(today, today.plusDays(1)),
            skipped = setOf(today.plusDays(2)),
            today = today,
        )
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `un-procured dish never rolls`() {
        val moves = MealPlanReconciler.reconcile(
            pastUncooked = listOf(PastEntry("a", today.minusDays(1), procured = false)),
            occupied = emptySet(),
            skipped = emptySet(),
            today = today,
        )
        assertTrue(moves.isEmpty())
    }

    @Test
    fun `two procured dishes claim slots oldest-first`() {
        val moves = MealPlanReconciler.reconcile(
            pastUncooked = listOf(
                PastEntry("newer", today.minusDays(1), procured = true),
                PastEntry("older", today.minusDays(3), procured = true),
            ),
            occupied = emptySet(),
            skipped = emptySet(),
            today = today,
        ).associate { it.entryId to it.toDate }

        assertEquals(today, moves["older"])          // oldest takes the earliest slot
        assertEquals(today.plusDays(1), moves["newer"]) // next-free for the newer one
    }

    @Test
    fun `cooked entries are filtered out by the caller and absent here produce no moves`() {
        // Reconcile only ever receives un-cooked entries; an empty list yields nothing.
        val moves = MealPlanReconciler.reconcile(
            pastUncooked = emptyList(),
            occupied = emptySet(),
            skipped = emptySet(),
            today = today,
        )
        assertTrue(moves.isEmpty())
    }
}
