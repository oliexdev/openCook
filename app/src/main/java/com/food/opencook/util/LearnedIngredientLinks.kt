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

/**
 * Runtime holder for the household's learned "these two are NOT the same product" lessons
 * (the `ingredient_links` table, taught via the shopping list's "brauch ich doch" chip).
 * A `@Volatile` snapshot mirrored from `IngredientLinkRepository.observeLinks()` by an
 * app-scoped collector — the same shape as how `LocalizedLists` pushes data into
 * [IngredientStaples]/[IngredientLexicon], so [IngredientMatch] stays a stateless predicate.
 *
 * Consulted (symmetrically) as the highest-priority short-circuit in [IngredientMatch]:
 * a learned distinction blocks coverage in both directions.
 */
object LearnedIngredientLinks {

    @Volatile private var distinctKeys: Set<String> = emptySet()

    /** Replace the learned distinctions from the observed table (names are raw; normalized here). */
    fun setDistinct(pairs: Collection<Pair<String, String>>) {
        distinctKeys = pairs.mapNotNull { (a, b) ->
            val na = IngredientMatch.normalizeName(a)
            val nb = IngredientMatch.normalizeName(b)
            if (na.isEmpty() || nb.isEmpty() || na == nb) null else IngredientLexicon.pairKey(na, nb)
        }.toSet()
    }

    /** True if the two already-normalized words were taught to be distinct. */
    fun isDistinct(a: String, b: String): Boolean =
        distinctKeys.isNotEmpty() && distinctKeys.contains(IngredientLexicon.pairKey(a, b))
}
