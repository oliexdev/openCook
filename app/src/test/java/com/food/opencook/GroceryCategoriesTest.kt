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
