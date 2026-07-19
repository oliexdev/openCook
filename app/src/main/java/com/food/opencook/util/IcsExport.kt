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

import com.food.opencook.ui.mealplan.DayPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds an RFC 5545 (.ics) calendar from a week's meal plan — one all-day VEVENT
 * per day that has at least one dish planned, so it imports cleanly into Google
 * Calendar (or any calendar app) via a plain file share.
 *
 * This is a **one-shot export**, not a live subscription: re-exporting after
 * changing the plan produces a new file that has to be re-imported by hand, since
 * a plain .ics import doesn't update previously-imported events. A true "subscribe
 * and auto-refresh" feed needs a stable, internet-reachable URL for Google's
 * calendar servers to poll — which the self-hosted server deliberately isn't
 * (LAN/VPN-only, see [com.food.opencook.data.settings.SettingsRepository]) — so
 * that's a separate, bigger feature with its own trust-model tradeoffs.
 */
object IcsExport {
    private val DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** One VCALENDAR containing every day in [week] that has planned dishes. Empty
     *  days are skipped rather than emitted as blank events. */
    fun buildWeekIcs(week: List<DayPlan>): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//openCook//Meal Plan Export//EN",
            "CALSCALE:GREGORIAN",
        )
        val stampedNow = utcTimestamp()
        week.forEach { day ->
            if (day.entries.isEmpty()) return@forEach
            val date = LocalDate.parse(day.date)
            val summary = day.entries.joinToString(" + ") { it.name }
            val description = day.entries.joinToString("\\n") { "- ${escape(it.name)}" }
            lines += listOf(
                "BEGIN:VEVENT",
                // Deterministic per date: re-exporting the same day again keeps the same UID,
                // so a calendar client that DOES understand updates (unlike a plain file
                // import) would treat it as a modification rather than a duplicate.
                "UID:${date.format(DATE_STAMP)}-opencook-mealplan@opencook.local",
                "DTSTAMP:$stampedNow",
                "DTSTART;VALUE=DATE:${date.format(DATE_STAMP)}",
                // All-day events use an exclusive end date in iCal — the day after.
                "DTEND;VALUE=DATE:${date.plusDays(1).format(DATE_STAMP)}",
                "SUMMARY:${escape(summary)}",
                "DESCRIPTION:$description",
                "TRANSP:TRANSPARENT",
                "END:VEVENT",
            )
        }
        lines += "END:VCALENDAR"
        // RFC 5545 requires CRLF line endings and folding long lines at 75 octets; Google
        // Calendar's importer is lenient about folding, but CRLF is worth getting right.
        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun utcTimestamp(): String =
        java.time.Instant.now().let {
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(java.time.ZoneOffset.UTC)
                .format(it)
        }

    /** Escape the handful of characters RFC 5545 TEXT values require escaped. Newlines
     *  are handled by callers joining with the literal "\\n" token, not a real newline. */
    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
}
