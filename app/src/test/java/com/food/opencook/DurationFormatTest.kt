package com.food.opencook

import com.food.opencook.util.DurationFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationFormatTest {

    @Test
    fun isoToHuman() {
        assertEquals("25 Min", DurationFormat.toHuman("PT25M"))
        assertEquals("1 Std", DurationFormat.toHuman("PT1H"))
        assertEquals("1 Std 10 Min", DurationFormat.toHuman("PT1H10M"))
        assertEquals("", DurationFormat.toHuman(null))
        assertEquals("", DurationFormat.toHuman(""))
    }

    @Test
    fun nonIsoPassesThrough() {
        assertEquals("über Nacht", DurationFormat.toHuman("über Nacht"))
    }

    @Test
    fun humanToIso() {
        assertEquals("PT25M", DurationFormat.toIso("25 Min"))
        assertEquals("PT70M", DurationFormat.toIso("1 Std 10 Min"))
        assertEquals("PT120M", DurationFormat.toIso("2 Std"))
        assertNull(DurationFormat.toIso(""))
        assertNull(DurationFormat.toIso(null))
    }

    @Test
    fun alreadyIsoIsKept() {
        assertEquals("PT25M", DurationFormat.toIso("PT25M"))
    }

    @Test
    fun unparseableTextStoredVerbatim() {
        assertEquals("über Nacht", DurationFormat.toIso("über Nacht"))
    }

    @Test
    fun roundTrips() {
        assertEquals("PT25M", DurationFormat.toIso(DurationFormat.toHuman("PT25M")))
        assertEquals("PT70M", DurationFormat.toIso(DurationFormat.toHuman("PT1H10M")))
    }
}
