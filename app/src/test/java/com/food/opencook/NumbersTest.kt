package com.food.opencook

import com.food.opencook.util.DurationFormat
import com.food.opencook.util.Numbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumbersTest {

    @Test
    fun parseQuantityHandlesCommaAndUnits() {
        assertEquals(400.0, Numbers.parseQuantity("400 g")!!, 0.001)
        assertEquals(1.5, Numbers.parseQuantity("1,5")!!, 0.001)
        assertEquals(2.0, Numbers.parseQuantity("2 Personen")!!, 0.001)
        assertNull(Numbers.parseQuantity("etwas"))
        assertNull(Numbers.parseQuantity(null))
    }

    @Test
    fun formatQuantityDropsTrailingZero() {
        assertEquals("400", Numbers.formatQuantity(400.0))
        assertEquals("1.5", Numbers.formatQuantity(1.5))
        assertNull(Numbers.formatQuantity(null))
    }

    @Test
    fun displayIngredientJoins() {
        assertEquals("400 g Nudeln", Numbers.displayIngredient(400.0, "g", "Nudeln"))
        assertEquals("Salz", Numbers.displayIngredient(null, null, "Salz"))
        assertEquals("1 Bund Basilikum", Numbers.displayIngredient(1.0, "Bund", "Basilikum"))
    }

    @Test
    fun scaleForHouseholdSize() {
        assertEquals(2.0, Numbers.scaleFor(servings = 2, target = 4), 0.001) // 2-portion recipe for 4
        assertEquals(0.5, Numbers.scaleFor(servings = 4, target = 2), 0.001)
        assertEquals(1.0, Numbers.scaleFor(servings = null, target = 4), 0.001) // unknown servings → no scaling
        assertEquals(1.0, Numbers.scaleFor(servings = 0, target = 4), 0.001)
    }

    @Test
    fun scaleQuantityRoundsAndKeepsNull() {
        assertEquals(800.0, Numbers.scaleQuantity(400.0, 2.0)!!, 0.001)
        assertEquals(0.33, Numbers.scaleQuantity(1.0, 1.0 / 3.0)!!, 0.001) // rounded to 2 decimals
        assertNull(Numbers.scaleQuantity(null, 2.0)) // unquantified (salt) stays null
    }

    @Test
    fun durationMinutes() {
        assertEquals(25, DurationFormat.minutes("PT25M"))
        assertEquals(70, DurationFormat.minutes("PT1H10M"))
        assertNull(DurationFormat.minutes(null))
        assertNull(DurationFormat.minutes("nonsense"))
    }
}
