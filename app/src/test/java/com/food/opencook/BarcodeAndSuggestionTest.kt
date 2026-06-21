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

import com.food.opencook.data.remote.dto.OffProductDto
import com.food.opencook.repository.ProductLookupRepository
import com.food.opencook.repository.SuggestionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeAndSuggestionTest {

    @Test
    fun displayNamePrefersGermanThenGeneric() {
        assertEquals("Vollmilch", ProductLookupRepository.displayName(OffProductDto(productNameDe = "Vollmilch", productName = "Whole milk")))
        assertEquals("Whole milk", ProductLookupRepository.displayName(OffProductDto(productNameDe = "  ", productName = "Whole milk")))
        assertNull(ProductLookupRepository.displayName(OffProductDto(productNameDe = "", productName = "")))
        assertNull(ProductLookupRepository.displayName(null))
    }

    @Test
    fun suggestionFilterPrefixBeforeContainsCaseInsensitive() {
        val pool = listOf("Tomaten", "Tomatenmark", "Kartoffeln", "Rote Tomate")
        val out = SuggestionRepository.filter(pool, "tom")
        // Prefix matches ("Tomaten", "Tomatenmark") before the "contains" match ("Rote Tomate").
        assertEquals(listOf("Tomaten", "Tomatenmark", "Rote Tomate"), out)
    }

    @Test
    fun suggestionFilterBlankQueryReturnsNothing() {
        assertEquals(emptyList<String>(), SuggestionRepository.filter(listOf("Tomaten"), "  "))
    }

    @Test
    fun dedupeKeepsFirstOccurrenceCaseInsensitive() {
        // Own data ("Tomaten") wins over a later built-in duplicate ("tomaten").
        val out = SuggestionRepository.dedupePreservingOrder(listOf("Tomaten", "Milch", "tomaten", "MILCH"))
        assertEquals(listOf("Tomaten", "Milch"), out)
    }
}
