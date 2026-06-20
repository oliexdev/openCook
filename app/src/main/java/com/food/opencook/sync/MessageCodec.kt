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
