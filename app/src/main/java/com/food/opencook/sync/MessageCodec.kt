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

package com.food.opencook.sync

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Decodes JSON-encoded message values back into typed column values. */
object MessageCodec {
    private val json = Json

    fun decodeString(value: String?): String? =
        if (value == null || value == "null") null else json.decodeFromString(String.serializer(), value)

    fun decodeInt(value: String?): Int = value?.toIntOrNull() ?: 0

    /** Nullable numeric column. Encoded as "null" or a bare number. */
    fun decodeNullableInt(value: String?): Int? =
        if (value == null || value == "null") null else value.toIntOrNull()

    fun decodeNullableDouble(value: String?): Double? =
        if (value == null || value == "null") null else value.toDoubleOrNull()

    fun isTrue(value: String?): Boolean = value == "true"
}
