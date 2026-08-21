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

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Where a recipe stands in the household's cooking rotation. Used as a single-select filter
 * group in the recipe list and as the retrospective's "you've forgotten these" rule — the two
 * have to agree, or "show all" from the retrospective would land on a different set than the
 * one it just listed.
 */
enum class CookedFilter { COOKED, NEVER, STALE }

object CookedStatus {

    /**
     * Past this many days a dish counts as forgotten. Three months is deliberately generous:
     * a household cooks maybe twenty dishes a season, so a shorter window would flag most of
     * the collection and say nothing.
     */
    const val STALE_DAYS = 90L

    /** Parse `recipes.lastCookedAt`. Unparseable (or absent) reads as "never cooked". */
    fun parse(iso: String?): LocalDate? =
        iso?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Days since the dish was last cooked, or null if it never was. Never negative — a date
     *  in the future (clock skew between synced devices) reads as "cooked today". */
    fun daysSince(iso: String?, today: LocalDate): Long? =
        parse(iso)?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0L) }

    /** Never cooked, or not for [STALE_DAYS]. Never-cooked counts on purpose: those are the
     *  most forgotten dishes of all, and a filter that hid them would be a strange one. */
    fun isStale(iso: String?, today: LocalDate): Boolean =
        (daysSince(iso, today) ?: Long.MAX_VALUE) >= STALE_DAYS

    /** The filter predicate. A null [filter] means the group is off and everything passes. */
    fun matches(filter: CookedFilter?, iso: String?, today: LocalDate): Boolean = when (filter) {
        null -> true
        CookedFilter.COOKED -> parse(iso) != null
        CookedFilter.NEVER -> parse(iso) == null
        CookedFilter.STALE -> isStale(iso, today)
    }
}
