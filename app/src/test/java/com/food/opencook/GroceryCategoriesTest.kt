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

import com.food.opencook.util.GroceryCategories
import com.food.opencook.util.GroceryCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class GroceryCategoriesTest {

    @Test
    fun categorizesCommonItems() {
        assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("Tomaten"))
        assertEquals(GroceryCategory.PRODUCE, GroceryCategories.categorize("2 rote Paprikaschoten"))
        assertEquals(GroceryCategory.MEAT_FISH, GroceryCategories.categorize("Barschfilet"))
        assertEquals(GroceryCategory.DAIRY, GroceryCategories.categorize("Vollmilch"))
        assertEquals(GroceryCategory.BAKERY, GroceryCategories.categorize("Brötchen"))
        assertEquals(GroceryCategory.SPICES, GroceryCategories.categorize("Olivenöl"))
    }

    @Test
    fun specificBeatsGeneric() {
        // "Kokosmilch" must NOT fall into DAIRY via "milch".
        assertEquals(GroceryCategory.PANTRY, GroceryCategories.categorize("Kokosmilch"))
    }

    @Test
    fun unknownFallsBackToOther() {
        assertEquals(GroceryCategory.OTHER, GroceryCategories.categorize("Klopapier"))
    }
}
