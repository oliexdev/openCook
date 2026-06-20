package com.food.opencook.util

/** Helpers for the structured numeric quantities/servings. */
object Numbers {

    /** Parse a quantity from text ("400", "1,5", "1.5 ") → 400.0 / 1.5; null if none. */
    fun parseQuantity(text: String?): Double? =
        text?.trim()?.replace(',', '.')?.let { Regex("""-?\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

    /** Render a quantity without a trailing ".0" (400.0 → "400", 1.5 → "1.5"). */
    fun formatQuantity(value: Double?): String? {
        if (value == null) return null
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    /** "400 g Nudeln" / "1 Bund Basilikum" / "Salz" — display join of quantity+unit+name. */
    fun displayIngredient(quantity: Double?, unit: String?, name: String): String =
        listOfNotNull(formatQuantity(quantity), unit?.takeIf { it.isNotBlank() }, name.takeIf { it.isNotBlank() })
            .joinToString(" ")

    /**
     * Factor to scale a recipe made for [servings] up/down to [target] people.
     * 1.0 (no scaling) when servings is unknown/zero — we never guess a baseline.
     */
    fun scaleFor(servings: Int?, target: Int): Double =
        if (servings != null && servings > 0 && target > 0) target.toDouble() / servings else 1.0

    /** Scale a quantity by [factor], rounded to 2 decimals. Null (unquantified, e.g. salt) stays null. */
    fun scaleQuantity(quantity: Double?, factor: Double): Double? =
        quantity?.let { Math.round(it * factor * 100.0) / 100.0 }
}

/** Fixed coarse categories the AI assigns; drives meal-plan variety. */
object RecipeCategories {
    val ALL = listOf("Pasta", "Fleisch", "Fisch", "Suppe", "Vegetarisch", "Salat", "Dessert", "Sonstiges")
    const val DEFAULT = "Sonstiges"
}
