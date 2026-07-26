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

import com.food.opencook.data.backup.MealPlanEntryBackup
import com.food.opencook.data.backup.toBackup
import com.food.opencook.data.backup.toEntity
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.sync.MealPlanMessageEncoder
import com.food.opencook.ui.mealplan.MealPlanSlots
import com.food.opencook.ui.mealplan.MealPlanner
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide *where* a planned dish belongs. The important guarantees: entries
 * written before slots existed stay visible, and a meal that isn't planned can still hold a
 * one-off for a single day.
 */
class MealPlanSlotsTest {

    @Test
    fun unsetPlannedMealsMeansTheOldSingleSlotBehaviour() {
        assertEquals(listOf("lunch"), MealPlanSlots.plannedFromStored(null))
        assertEquals(listOf("lunch"), MealPlanSlots.plannedFromStored(""))
    }

    @Test
    fun plannedMealsAlwaysComeBackInOrderOfTheDay() {
        assertEquals(
            listOf("breakfast", "lunch", "dinner"),
            MealPlanSlots.plannedFromStored("dinner\nbreakfast\nlunch"),
        )
    }

    @Test
    fun legacyEntriesLandOnLunch() {
        assertEquals("lunch", MealPlanSlots.resolve(null, listOf("breakfast", "lunch", "dinner")))
    }

    @Test
    fun legacyEntriesStayVisibleWhenLunchIsNotPlanned() {
        // Never return a slot the day won't render — an old entry must not disappear.
        assertEquals("breakfast", MealPlanSlots.resolve(null, listOf("breakfast", "dinner")))
        assertEquals("dinner", MealPlanSlots.resolve(null, listOf("dinner")))
    }

    @Test
    fun storedSlotWinsOverTheDefault() {
        assertEquals("dinner", MealPlanSlots.resolve("dinner", listOf("lunch")))
        // Unknown values are not guessed at; they fall back like an unset one.
        assertEquals("lunch", MealPlanSlots.resolve("brunch", listOf("lunch")))
    }

    @Test
    fun oneOffsInUnplannedMealsStayBelowTheDay() {
        // Sunday cake while "snack" is switched off: the row shows up for that day, but at
        // the bottom — it must not wedge itself between lunch and dinner.
        assertEquals(
            listOf("lunch", "dinner", "snack"),
            MealPlanSlots.rowsFor(planned = listOf("lunch", "dinner"), occupied = setOf("snack")),
        )
        // Enabling it in the settings is what moves it into its place in the day.
        assertEquals(
            listOf("lunch", "snack", "dinner"),
            MealPlanSlots.rowsFor(planned = listOf("lunch", "snack", "dinner"), occupied = setOf("snack")),
        )
    }

    @Test
    fun currentSlotFollowsTheClockAndFallsBackToWhatIsPlanned() {
        val all = listOf("breakfast", "lunch", "snack", "dinner")
        assertEquals("breakfast", MealPlanSlots.currentSlot(all, hour = 8))
        assertEquals("lunch", MealPlanSlots.currentSlot(all, hour = 12))
        assertEquals("snack", MealPlanSlots.currentSlot(all, hour = 15))
        assertEquals("dinner", MealPlanSlots.currentSlot(all, hour = 19))
        // Morning with only dinner planned → the next meal of the day, not yesterday's.
        assertEquals("dinner", MealPlanSlots.currentSlot(listOf("dinner"), hour = 8))
        // Evening with only breakfast planned → the day's last planned meal.
        assertEquals("breakfast", MealPlanSlots.currentSlot(listOf("breakfast"), hour = 22))
    }

    @Test
    fun slotTravelsTheSyncLogAndClearsExplicitly() {
        val entry = MealPlanEntity(
            id = "e1", date = "2026-08-03", recipeId = "r1", slot = "breakfast",
            createdAt = 0, updatedAt = 0,
        )
        assertEquals("\"breakfast\"", MealPlanMessageEncoder.encode(entry).first { it.column == "slot" }.value)
        // An entry that predates slots emits an explicit null, so LWW can overwrite a
        // stale value instead of leaving it stuck.
        assertEquals("null", MealPlanMessageEncoder.encode(entry.copy(slot = null)).first { it.column == "slot" }.value)
    }

    @Test
    fun backupCarriesTheSlotAndOlderArchivesStillRestore() {
        val entry = MealPlanEntity(
            id = "e1", date = "2026-08-03", recipeId = "r1", slot = "dinner",
            createdAt = 1, updatedAt = 2,
        )
        assertEquals(entry, entry.toBackup().toEntity())
        // An archive written before slots existed has no field at all → null, which
        // resolves to the primary meal on restore rather than failing to read.
        val old = Json.decodeFromString(
            MealPlanEntryBackup.serializer(),
            """{"id":"e1","date":"2026-08-03","recipeId":"r1"}""",
        )
        assertEquals(null, old.slot)
        assertEquals("lunch", MealPlanSlots.resolve(old.toEntity().slot, listOf("lunch", "dinner")))
    }

    @Test
    fun habitualMealsTolerateRepetitionOfTheSameDish() {
        val breakfast = MealPlanner.Weights.forSlot("breakfast")
        val dinner = MealPlanner.Weights.forSlot("dinner")
        // Porridge four mornings running is what people eat; the same dish twice for
        // dinner in a week is a planning failure. So the variety machinery differs.
        assertTrue(breakfast.recencyPenalty < dinner.recencyPenalty)
        assertTrue(breakfast.monotonyPenalty < dinner.monotonyPenalty)
        assertEquals(0.0, breakfast.sameCategoryPenalty, 0.0)
        assertEquals(0.0, breakfast.sameProteinPenalty, 0.0)
        assertEquals(MealPlanner.Weights(), dinner)
        assertEquals(MealPlanner.Weights(), MealPlanner.Weights.forSlot("lunch"))
    }
}
