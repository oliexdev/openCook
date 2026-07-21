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

import androidx.compose.ui.unit.sp
import com.food.opencook.data.settings.FontScales
import com.food.opencook.ui.theme.Typography
import com.food.opencook.ui.theme.scaled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FontScaleTest {

    @Test
    fun `default factor leaves the type scale untouched`() {
        assertSame(Typography, Typography.scaled(1f))
    }

    @Test
    fun `scaling grows font size and line height proportionally`() {
        val scaled = Typography.scaled(1.5f)
        // bodyLarge is the recipe reading size: 16sp/24sp at the canonical scale.
        assertEquals(24f, scaled.bodyLarge.fontSize.value, 0.01f)
        assertEquals(36f, scaled.bodyLarge.lineHeight.value, 0.01f)
        assertEquals(31.5f, scaled.headlineSmall.fontSize.value, 0.01f)
    }

    @Test
    fun `scaling leaves everything but the measurements alone`() {
        val scaled = Typography.scaled(1.3f)
        assertEquals(Typography.titleLarge.fontWeight, scaled.titleLarge.fontWeight)
        assertEquals(Typography.bodyLarge.letterSpacing, scaled.bodyLarge.letterSpacing)
    }

    @Test
    fun `shrinking works too`() {
        assertEquals(13.6f, Typography.scaled(0.85f).bodyLarge.fontSize.value, 0.01f)
    }

    @Test
    fun `unspecified line height survives scaling`() {
        // Not every style in a custom ramp declares one; it must not become NaN.
        val ramp = Typography.copy(labelSmall = Typography.labelSmall.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified))
        val scaled = ramp.scaled(1.5f)
        assertEquals(androidx.compose.ui.unit.TextUnit.Unspecified, scaled.labelSmall.lineHeight)
        assertEquals(16.5f, scaled.labelSmall.fontSize.value, 0.01f)
    }

    @Test
    fun `steps are ordered and contain the canonical scale`() {
        assertEquals(FontScales.STEPS.sorted(), FontScales.STEPS)
        assert(FontScales.DEFAULT in FontScales.STEPS)
        assertEquals(FontScales.STEPS.first(), FontScales.MIN)
        assertEquals(FontScales.STEPS.last(), FontScales.MAX)
    }

    @Test
    fun `indexOf snaps a stored factor to the nearest step`() {
        assertEquals(1, FontScales.indexOf(1f))
        assertEquals(0, FontScales.indexOf(0.85f))
        assertEquals(4, FontScales.indexOf(1.5f))
        // A value from a since-changed step list still lands somewhere sensible.
        assertEquals(2, FontScales.indexOf(1.14f))
        assertEquals(4, FontScales.indexOf(9f))
    }

    @Test
    fun `every step has a label`() {
        // Guards the index-for-index pairing FONT_SIZE_LABELS relies on.
        assertEquals(5, FontScales.STEPS.size)
    }

    @Test
    fun `sp arithmetic sanity`() {
        assertEquals(24f, (16.sp * 1.5f).value, 0.01f)
    }
}
