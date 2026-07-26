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

package com.food.opencook.util

import com.food.opencook.data.local.relation.RecipeWithDetails

/**
 * "Can I cook this right now?" — the single definition of it.
 *
 * Two rules, and both matter: staples (salt, oil, broth …) count as always on hand, because
 * nobody keeps them in the pantry list, and matching is plural-tolerant, so "Zwiebel" in the
 * recipe is covered by "Zwiebeln" in the pantry. Screens used to spell this out themselves
 * and drifted apart — the same dish could read "Alles da" in the planner and "3 fehlen"
 * elsewhere.
 *
 * [pantry] is expected lowercased and trimmed (what the pantry list stores).
 */
object RecipeAvailability {

    /** Ingredient names the pantry does not cover. Empty means the dish is cookable now. */
    fun missing(recipe: RecipeWithDetails, pantry: Set<String>): List<String> =
        recipe.ingredients.mapNotNull { ing ->
            ing.name.trim().takeIf {
                it.isNotEmpty() &&
                    !IngredientStaples.isStaple(it) &&
                    !IngredientMatch.containsLike(pantry, it)
            }
        }

    /**
     * Everything for this dish is on hand. A recipe with no ingredients at all is *not*
     * counted as stocked: it is unfinished data, not a meal you can start cooking.
     */
    fun isStocked(recipe: RecipeWithDetails, pantry: Set<String>): Boolean =
        recipe.ingredients.isNotEmpty() && missing(recipe, pantry).isEmpty()
}
