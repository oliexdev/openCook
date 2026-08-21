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

import com.food.opencook.data.local.dao.RecipeLikeCount
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.ui.retrospect.Retrospective
import com.food.opencook.util.CookedFilter
import com.food.opencook.util.CookedStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The retrospective's counting rules, and the cooking-status predicate it shares with the
 * recipe list's filter sheet.
 */
class RetrospectiveTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 17)

    private fun recipe(id: String, name: String, lastCookedAt: String? = null) =
        RecipeEntity(id = id, name = name, lastCookedAt = lastCookedAt, createdAt = 0L, updatedAt = 0L)

    private fun cooked(recipeId: String, date: String) = MealPlanEntity(
        id = "$recipeId@$date",
        date = date,
        recipeId = recipeId,
        cookedAt = date,
        createdAt = 0L,
        updatedAt = 0L,
    )

    // --- The month figures ------------------------------------------------

    @Test
    fun `month counts only the current month`() {
        val data = Retrospective.build(
            cooked = listOf(cooked("a", "2026-08-02"), cooked("a", "2026-08-16"), cooked("a", "2026-07-30")),
            recipes = listOf(recipe("a", "Chili")),
            likes = emptyList(),
            votingDevices = 0,
            today = today,
        )
        assertEquals(2, data.monthCooked)
        assertEquals(17, data.monthDaysElapsed)
    }

    @Test
    fun `two meals on one day are two meals but one day`() {
        val data = Retrospective.build(
            cooked = listOf(cooked("a", "2026-08-05"), cooked("b", "2026-08-05")),
            recipes = listOf(recipe("a", "Chili"), recipe("b", "Suppe")),
            likes = emptyList(),
            votingDevices = 0,
            today = today,
        )
        assertEquals(2, data.monthCooked)
        assertEquals(1, data.monthDaysCooked)
    }

    @Test
    fun `a meal dated after today does not count towards this month`() {
        // Plan rows exist for the future; only a confirmed cook in the past should count.
        val data = Retrospective.build(
            cooked = listOf(cooked("a", "2026-08-25")),
            recipes = listOf(recipe("a", "Chili")),
            likes = emptyList(),
            votingDevices = 0,
            today = today,
        )
        assertEquals(0, data.monthCooked)
    }

    // --- Most cooked ------------------------------------------------------

    @Test
    fun `most cooked ranks by count and caps the block`() {
        val recipes = (1..7).map { recipe("r$it", "Gericht $it") }
        val entries = recipes.flatMapIndexed { index, r ->
            List(7 - index) { n -> cooked(r.id, "2026-08-%02d".format(n + 1)) }
        }
        val data = Retrospective.build(entries, recipes, emptyList(), 0, today)

        assertEquals(Retrospective.BLOCK_SIZE, data.mostCooked.size)
        assertEquals("r1", data.mostCooked.first().recipeId)
        assertEquals(7, data.mostCooked.first().times)
        assertTrue(data.mostCooked.zipWithNext().all { (a, b) -> a.times >= b.times })
    }

    @Test
    fun `entries for a deleted recipe are skipped, not shown nameless`() {
        // Deleting a recipe tombstones the row but leaves its plan entries behind.
        val data = Retrospective.build(
            cooked = listOf(cooked("gone", "2026-08-04"), cooked("a", "2026-08-05")),
            recipes = listOf(recipe("a", "Chili")),
            likes = emptyList(),
            votingDevices = 0,
            today = today,
        )
        assertEquals(1, data.monthCooked)
        assertEquals(listOf("a"), data.mostCooked.map { it.recipeId })
    }

    // --- Household favourites ---------------------------------------------

    @Test
    fun `favourites stay empty while only one device has ever voted`() {
        val data = Retrospective.build(
            cooked = emptyList(),
            recipes = listOf(recipe("a", "Lasagne")),
            likes = listOf(RecipeLikeCount("a", 2)),
            votingDevices = 1,
            today = today,
        )
        assertTrue(data.favourites.isEmpty())
    }

    @Test
    fun `favourites need at least two likes`() {
        val data = Retrospective.build(
            cooked = emptyList(),
            recipes = listOf(recipe("a", "Lasagne"), recipe("b", "Suppe")),
            likes = listOf(RecipeLikeCount("a", 3), RecipeLikeCount("b", 1)),
            votingDevices = 3,
            today = today,
        )
        assertEquals(listOf("a"), data.favourites.map { it.recipeId })
        assertEquals(3, data.favourites.first().likes)
    }

    // --- Long uncooked ----------------------------------------------------

    @Test
    fun `long uncooked puts never-cooked first, then oldest`() {
        val data = Retrospective.build(
            cooked = emptyList(),
            recipes = listOf(
                recipe("fresh", "Frisch", today.minusDays(3).toString()),
                recipe("old", "Alt", today.minusDays(200).toString()),
                recipe("older", "Älter", today.minusDays(400).toString()),
                recipe("never", "Nie"),
            ),
            likes = emptyList(),
            votingDevices = 0,
            today = today,
        )
        assertEquals(listOf("never", "older", "old"), data.longUncooked.map { it.recipeId })
        assertEquals(null, data.longUncooked.first().daysSince)
    }

    @Test
    fun `a dish cooked just inside the window is not stale`() {
        val justInside = today.minusDays(CookedStatus.STALE_DAYS - 1).toString()
        val data = Retrospective.build(
            cooked = emptyList(),
            recipes = listOf(recipe("a", "Chili", justInside)),
            likes = emptyList(),
            votingDevices = 0,
            today = today,
        )
        assertTrue(data.longUncooked.isEmpty())
    }

    // --- Empty household --------------------------------------------------

    @Test
    fun `an empty household reports empty rather than zeroes`() {
        val data = Retrospective.build(emptyList(), emptyList(), emptyList(), 0, today)
        assertTrue(data.isEmpty)
    }

    @Test
    fun `a household with only forgotten recipes is not empty`() {
        val data = Retrospective.build(emptyList(), listOf(recipe("a", "Nie")), emptyList(), 0, today)
        assertFalse(data.isEmpty)
    }

    // --- The shared predicate ---------------------------------------------

    @Test
    fun `cooked status predicate splits the three states`() {
        val never: String? = null
        val recent = today.minusDays(2).toString()
        val ancient = today.minusDays(365).toString()

        assertTrue(CookedStatus.matches(CookedFilter.NEVER, never, today))
        assertFalse(CookedStatus.matches(CookedFilter.NEVER, recent, today))

        assertTrue(CookedStatus.matches(CookedFilter.COOKED, recent, today))
        assertFalse(CookedStatus.matches(CookedFilter.COOKED, never, today))

        assertTrue(CookedStatus.matches(CookedFilter.STALE, ancient, today))
        assertTrue(CookedStatus.matches(CookedFilter.STALE, never, today))
        assertFalse(CookedStatus.matches(CookedFilter.STALE, recent, today))

        // Group off — everything passes.
        assertTrue(CookedStatus.matches(null, never, today))
        assertTrue(CookedStatus.matches(null, recent, today))
    }

    @Test
    fun `an unparseable or future date degrades safely`() {
        assertTrue(CookedStatus.matches(CookedFilter.NEVER, "not-a-date", today))
        assertEquals(0L, CookedStatus.daysSince(today.plusDays(5).toString(), today))
    }
}
