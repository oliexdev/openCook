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

package com.food.opencook.repository

import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.dao.ProductCacheDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.dao.ShoppingDao
import com.food.opencook.util.CommonGroceries
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies name suggestions for the manual add-fields (shopping & pantry) and the
 * review-/import-time ingredient corrector. Purely local/offline: the user's own data
 * (recipe ingredients, past shopping & pantry items, cached scanned products) ranked first,
 * then the built-in [CommonGroceries] vocabulary.
 */
@Singleton
class SuggestionRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val shoppingDao: ShoppingDao,
    private val pantryDao: PantryDao,
    private val productCacheDao: ProductCacheDao,
) {
    /**
     * The curated, bundled vocabulary only ([CommonGroceries]) — no user/DB data. Used as safe
     * correction targets at import time, where snapping to a messy stored entry must be impossible.
     */
    fun curatedTerms(): List<String> = CommonGroceries.LIST

    /** The de-duplicated suggestion pool, own data first. Loaded once per add session. */
    suspend fun pool(): List<String> {
        val own = recipeDao.distinctIngredientNames() +
            shoppingDao.distinctTexts() +
            pantryDao.allNames() +
            productCacheDao.distinctNames()
        return dedupePreservingOrder(
            own.map { it.trim() }.filter { it.isNotEmpty() } + CommonGroceries.LIST,
        )
    }

    companion object {
        /** Case-insensitive dedupe keeping first occurrence (so own data wins over built-ins). */
        fun dedupePreservingOrder(names: List<String>): List<String> {
            val seen = HashSet<String>()
            val out = ArrayList<String>()
            for (n in names) if (seen.add(n.lowercase())) out.add(n)
            return out
        }

        /**
         * Filter [pool] by [query]: prefix matches first, then "contains", case-insensitive.
         * Blank query → no suggestions (don't nag before the user types).
         */
        fun filter(pool: List<String>, query: String, limit: Int = 8): List<String> {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return emptyList()
            val prefix = pool.filter { it.lowercase().startsWith(q) }
            val contains = pool.filter { val l = it.lowercase(); !l.startsWith(q) && l.contains(q) }
            return (prefix + contains).take(limit)
        }
    }
}
