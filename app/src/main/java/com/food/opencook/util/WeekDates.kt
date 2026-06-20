package com.food.opencook.util

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calendar-week math: always Monday–Sunday, with optional week offset.
 * Shared by the meal planner (current vs. next week toggle) and the
 * recipe → meal-plan picker sheet.
 */
object WeekDates {

    /** All seven days Mon–Sun of the week containing [reference], offset by [weekOffset] full weeks. */
    fun weekOf(reference: LocalDate = LocalDate.now(), weekOffset: Int = 0): List<LocalDate> {
        val monday = mondayOf(reference, weekOffset)
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    /** The Monday of the week containing [reference], shifted by [weekOffset] weeks. */
    fun mondayOf(reference: LocalDate = LocalDate.now(), weekOffset: Int = 0): LocalDate =
        reference.with(DayOfWeek.MONDAY).plusWeeks(weekOffset.toLong())
}
