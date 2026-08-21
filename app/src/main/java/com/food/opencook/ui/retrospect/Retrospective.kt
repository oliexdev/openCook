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

package com.food.opencook.ui.retrospect

import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.util.CookedStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * The retrospective's arithmetic, kept as pure functions so the grouping and ordering are
 * testable without a device — same arrangement as `MealPlanner` and `RecipeSearchFilter`.
 *
 * It reads a by-product of features that already exist: `meal_plan` is never pruned and
 * records a `cookedAt` for planned *and* off-plan meals, so the household's whole cooking
 * history is already on disk and already syncs. Nothing here needs a new column.
 */
object Retrospective {

    /** One confirmed meal. */
    data class Meal(val recipeId: String, val name: String, val date: LocalDate)

    /** All meals of one calendar month, newest day first. */
    data class MonthGroup(val month: YearMonth, val meals: List<Meal>) {
        val count: Int get() = meals.size
    }

    /**
     * Group every confirmed meal by calendar month, newest month first and, inside a month,
     * newest day first — from the most recent meal back to the very first one the household
     * ever confirmed.
     *
     * @param cooked plan entries carrying a `cookedAt`. Entries pointing at a deleted or
     *   nameless recipe are skipped rather than listed blank — deleting a recipe tombstones
     *   the row but leaves its plan entries behind.
     */
    fun byMonth(cooked: List<MealPlanEntity>, recipes: List<RecipeEntity>): List<MonthGroup> {
        val byId = recipes.filter { !it.name.isNullOrBlank() }.associateBy { it.id }

        return cooked
            .mapNotNull { entry ->
                val recipe = byId[entry.recipeId] ?: return@mapNotNull null
                val date = CookedStatus.parse(entry.cookedAt) ?: return@mapNotNull null
                Meal(entry.recipeId, recipe.name.orEmpty(), date)
            }
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap(reverseOrder())
            .map { (month, meals) ->
                // Two meals on the same day (lunch and dinner) keep a stable order by name —
                // the plan's slot is not carried here, and an unstable list would reshuffle
                // itself between two identical reads.
                MonthGroup(month, meals.sortedWith(compareByDescending<Meal> { it.date }.thenBy { it.name }))
            }
    }
}
