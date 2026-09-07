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
import java.time.YearMonth

/**
 * The retrospective's grouping rules, and the cooking-status predicate it shares with the
 * recipe list's filter sheet.
 */
class RetrospectiveTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 17)

    private fun recipe(id: String, name: String?) =
        RecipeEntity(id = id, name = name, createdAt = 0L, updatedAt = 0L)

    /** A plan entry confirmed as cooked. [planned] differs from [cookedAt] for an off-plan meal. */
    private fun cooked(recipeId: String, cookedAt: String?, planned: String = cookedAt.orEmpty()) =
        MealPlanEntity(
            id = "$recipeId@$planned",
            date = planned,
            recipeId = recipeId,
            cookedAt = cookedAt,
            createdAt = 0L,
            updatedAt = 0L,
        )

    private fun month(y: Int, m: Int) = YearMonth.of(y, m)

    // --- Grouping and order -----------------------------------------------

    @Test
    fun `months run newest first, and days within a month too`() {
        val groups = Retrospective.byMonth(
            cooked = listOf(
                cooked("a", "2026-06-30"),
                cooked("a", "2026-08-02"),
                cooked("b", "2026-08-16"),
                cooked("a", "2026-07-14"),
            ),
            recipes = listOf(recipe("a", "Chili"), recipe("b", "Suppe")),
        )

        assertEquals(listOf(month(2026, 8), month(2026, 7), month(2026, 6)), groups.map { it.month })
        assertEquals(
            listOf(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 2)),
            groups.first().meals.map { it.date },
        )
    }

    @Test
    fun `two meals on one day keep a stable order by name`() {
        // Nothing in the data orders lunch against dinner — the plan's slot is not carried
        // here — so the tie is broken by name, or the list would reshuffle between two reads.
        val groups = Retrospective.byMonth(
            cooked = listOf(cooked("b", "2026-08-05"), cooked("a", "2026-08-05")),
            recipes = listOf(recipe("a", "Zwiebelsuppe"), recipe("b", "Auflauf")),
        )

        val meals = groups.single().meals
        assertEquals(listOf("Auflauf", "Zwiebelsuppe"), meals.map { it.name })
        assertEquals(2, groups.single().count)
    }

    @Test
    fun `a meal is grouped by the day it was cooked, not the day it was planned`() {
        // Cooking a dish a day late (or off-plan entirely) must move it in the history.
        val groups = Retrospective.byMonth(
            cooked = listOf(cooked("a", cookedAt = "2026-08-01", planned = "2026-07-31")),
            recipes = listOf(recipe("a", "Chili")),
        )

        assertEquals(month(2026, 8), groups.single().month)
        assertEquals(LocalDate.of(2026, 8, 1), groups.single().meals.single().date)
    }

    @Test
    fun `a meal carries the recipe id and its current name`() {
        val meal = Retrospective.byMonth(
            cooked = listOf(cooked("a", "2026-08-05")),
            recipes = listOf(recipe("a", "Chili")),
        ).single().meals.single()

        assertEquals("a", meal.recipeId)
        assertEquals("Chili", meal.name)
    }

    // --- What gets skipped ------------------------------------------------

    @Test
    fun `entries for a deleted recipe are skipped, not shown nameless`() {
        // Deleting a recipe tombstones the row but leaves its plan entries behind.
        val groups = Retrospective.byMonth(
            cooked = listOf(cooked("gone", "2026-08-04"), cooked("a", "2026-08-05")),
            recipes = listOf(recipe("a", "Chili")),
        )

        assertEquals(listOf("a"), groups.single().meals.map { it.recipeId })
        assertEquals(1, groups.single().count)
    }

    @Test
    fun `a nameless recipe is skipped rather than listed blank`() {
        val groups = Retrospective.byMonth(
            cooked = listOf(cooked("blank", "2026-08-04"), cooked("nul", "2026-08-04")),
            recipes = listOf(recipe("blank", "   "), recipe("nul", null)),
        )

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `an entry with an unusable cooked date is skipped`() {
        val groups = Retrospective.byMonth(
            cooked = listOf(
                cooked("a", null),
                cooked("a", ""),
                cooked("a", "not-a-date"),
                cooked("a", "2026-08-05"),
            ),
            recipes = listOf(recipe("a", "Chili")),
        )

        assertEquals(1, groups.single().count)
    }

    @Test
    fun `a household that has never cooked gets no months at all`() {
        assertTrue(Retrospective.byMonth(emptyList(), emptyList()).isEmpty())
        assertTrue(Retrospective.byMonth(emptyList(), listOf(recipe("a", "Nie gekocht"))).isEmpty())
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
    fun `a dish cooked just inside the window is not stale`() {
        assertFalse(CookedStatus.isStale(today.minusDays(CookedStatus.STALE_DAYS - 1).toString(), today))
        assertTrue(CookedStatus.isStale(today.minusDays(CookedStatus.STALE_DAYS).toString(), today))
    }

    @Test
    fun `an unparseable or future date degrades safely`() {
        assertTrue(CookedStatus.matches(CookedFilter.NEVER, "not-a-date", today))
        assertEquals(0L, CookedStatus.daysSince(today.plusDays(5).toString(), today))
    }
}
