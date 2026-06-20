package com.food.opencook.data.recipeimport

import com.food.opencook.data.remote.dto.IngredientDto

/**
 * Best-effort parser for a free-text ingredient line ("600 g Hackfleisch, halb und halb",
 * "1/2 TL Kümmel", "2 Becher Schmand", "Salz und Pfeffer") into structured
 * quantity / unit / name, so imported recipes can be scaled. Conservative: a leading
 * amount is only taken when it really starts with a number, and a unit only when the next
 * token is a known one — otherwise the whole line stays as the name.
 */
object IngredientLineParser {

    private val DEFAULT_UNITS_DE = setOf(
        // metric / volume
        "g", "kg", "mg", "l", "ml", "cl", "dl", "liter", "gramm", "kilogramm",
        // spoons / tips
        "el", "tl", "msp", "esslöffel", "teelöffel", "messerspitze",
        // counts / containers
        "prise", "prisen", "stück", "stk", "bund", "becher", "dose", "dosen", "glas", "gläser",
        "pck", "packung", "päckchen", "beutel", "tube", "flasche", "kugel", "kugeln",
        "zehe", "zehen", "scheibe", "scheiben", "blatt", "blätter", "tasse", "tassen",
        "tropfen", "cm", "kopf", "köpfe", "knolle", "knollen", "stange", "stangen",
        "zweig", "zweige", "handvoll", "schuss", "spritzer", "würfel", "portion", "portionen",
        "ecke", "ecken", "riegel", "topf",
        // common English (schema.org imports)
        "cup", "cups", "tbsp", "tsp", "oz", "lb", "clove", "cloves", "pinch", "can", "slice", "slices",
    )

    /** Active unit vocabulary; swapped at runtime by `LocalizedLists` to the content language.
     *  Defaults to the German+English set so unit tests / a fresh process still parse units. */
    @Volatile
    private var UNITS: Set<String> = DEFAULT_UNITS_DE

    /** Replace the recognized units (called by `LocalizedLists` on language change). */
    fun setUnits(units: Set<String>) {
        if (units.isNotEmpty()) UNITS = units
    }

    private val UNICODE_FRACTIONS = mapOf(
        '½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    // "1 1/2" (mixed) | "1/2" (fraction) | "1,5"/"1.5"/"600" with optional "2-3" range.
    private val LEADING_QTY = Regex(
        """^(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?(?:\s*[-–]\s*\d+(?:[.,]\d+)?)?)\s*""",
    )

    fun parse(raw: String): IngredientDto {
        val s = raw.trim()
        if (s.isEmpty()) return IngredientDto(null, null, raw.trim())

        // Leading unicode fraction, e.g. "½ TL Salz".
        UNICODE_FRACTIONS[s.firstOrNull()]?.let { value ->
            val (unit, name) = splitUnit(s.drop(1).trim())
            return IngredientDto(value, unit, name)
        }

        val match = LEADING_QTY.find(s) ?: return IngredientDto(null, null, s)
        val quantity = parseQuantity(match.groupValues[1]) ?: return IngredientDto(null, null, s)
        val rest = s.substring(match.range.last + 1).trim()
        val (unit, name) = splitUnit(rest)
        return IngredientDto(quantity, unit, name.ifBlank { s })
    }

    private fun parseQuantity(token: String): Double? {
        val t = token.trim()
        return when {
            t.contains(' ') && t.contains('/') -> { // mixed "1 1/2"
                val (whole, frac) = t.split(Regex("\\s+"), limit = 2)
                whole.toDoubleOrNull()?.let { w -> fraction(frac)?.let { w + it } }
            }
            t.contains('/') -> fraction(t)
            t.contains('-') || t.contains('–') -> // range → take the lower bound
                t.split('-', '–').first().replace(',', '.').toDoubleOrNull()
            else -> t.replace(',', '.').toDoubleOrNull()
        }
    }

    private fun fraction(t: String): Double? {
        val (n, d) = t.split('/').let { if (it.size == 2) it else return null }
        val nn = n.toDoubleOrNull() ?: return null
        val dd = d.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        return nn / dd
    }

    /** Split "<unit> <name>"; unit only when the first token is a known one. */
    private fun splitUnit(rest: String): Pair<String?, String> {
        if (rest.isBlank()) return null to rest
        val parts = rest.split(Regex("\\s+"), limit = 2)
        val candidate = parts[0]
        val norm = candidate.lowercase()
            .removeSuffix(".").removeSuffix("/n").removeSuffix("(n)").removeSuffix("(en)").removeSuffix(".")
        return if (parts.size == 2 && norm in UNITS) candidate to parts[1].trim() else null to rest
    }
}
