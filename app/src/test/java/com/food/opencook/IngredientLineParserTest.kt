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

import com.food.opencook.data.recipeimport.IngredientLineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientLineParserTest {

    private fun p(s: String) = IngredientLineParser.parse(s)

    @Test fun amountUnitName() {
        val i = p("600 g Hackfleisch, halb und halb")
        assertEquals(600.0, i.quantity!!, 1e-9)
        assertEquals("g", i.unit)
        assertEquals("Hackfleisch, halb und halb", i.name)
    }

    @Test fun fractionAttachedToUnit() {
        val i = p("1/2TL Kümmel, ganzer")
        assertEquals(0.5, i.quantity!!, 1e-9)
        assertEquals("TL", i.unit)
        assertEquals("Kümmel, ganzer", i.name)
    }

    @Test fun mixedNumber() {
        val i = p("1 1/2 EL Zucker")
        assertEquals(1.5, i.quantity!!, 1e-9)
        assertEquals("EL", i.unit)
        assertEquals("Zucker", i.name)
    }

    @Test fun decimalWithComma() {
        val i = p("1,5 l Wasser")
        assertEquals(1.5, i.quantity!!, 1e-9)
        assertEquals("l", i.unit)
        assertEquals("Wasser", i.name)
    }

    @Test fun containerUnit() {
        val i = p("2 Becher Schmand")
        assertEquals(2.0, i.quantity!!, 1e-9)
        assertEquals("Becher", i.unit)
        assertEquals("Schmand", i.name)
    }

    @Test fun zehenUnitWithSlash() {
        val i = p("1 Zehe/n Knoblauch")
        assertEquals(1.0, i.quantity!!, 1e-9)
        assertEquals("Zehe/n", i.unit)
        assertEquals("Knoblauch", i.name)
    }

    @Test fun countWithoutUnit() {
        val i = p("4 Ei(er) (bei Bedarf)")
        assertEquals(4.0, i.quantity!!, 1e-9)
        assertNull(i.unit)
        assertEquals("Ei(er) (bei Bedarf)", i.name)
    }

    @Test fun adjectiveAfterNumberIsNotAUnit() {
        val i = p("1 m.-große Zwiebel(n)")
        assertEquals(1.0, i.quantity!!, 1e-9)
        assertNull(i.unit)
        assertEquals("m.-große Zwiebel(n)", i.name)
    }

    @Test fun noAmountKeepsWholeLine() {
        val i = p("Salz und Pfeffer")
        assertNull(i.quantity)
        assertNull(i.unit)
        assertEquals("Salz und Pfeffer", i.name)
    }

    @Test fun etwasIsNotAQuantity() {
        val i = p("etwas Wasser")
        assertNull(i.quantity)
        assertEquals("etwas Wasser", i.name)
    }

    @Test fun rangeTakesLowerBound() {
        val i = p("2-3 EL Öl")
        assertEquals(2.0, i.quantity!!, 1e-9)
        assertEquals("EL", i.unit)
        assertEquals("Öl", i.name)
    }
}
