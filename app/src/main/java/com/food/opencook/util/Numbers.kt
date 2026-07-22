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

import android.content.Context
import androidx.annotation.StringRes
import com.food.opencook.R

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

/**
 * Fixed coarse categories the AI assigns; drives meal-plan variety. Stored as
 * language-independent **keys** (e.g. "meat"); the UI shows a localized label.
 * Legacy/AI values in any language are mapped to a key on read via [normalizeKey].
 */
object RecipeCategories {
    /** Stable keys persisted in the DB + sync log. */
    val KEYS = listOf("pasta", "meat", "fish", "soup", "vegetarian", "salad", "dessert", "other")
    const val DEFAULT = "other"

    @StringRes
    fun labelRes(key: String?): Int = when (normalizeKey(key)) {
        "pasta" -> R.string.cat_pasta
        "meat" -> R.string.cat_meat
        "fish" -> R.string.cat_fish
        "soup" -> R.string.cat_soup
        "vegetarian" -> R.string.cat_vegetarian
        "salad" -> R.string.cat_salad
        "dessert" -> R.string.cat_dessert
        else -> R.string.cat_other
    }

    /** Map a stored/legacy/AI category (any language) to a stable key; unknown → [DEFAULT]. */
    fun normalizeKey(raw: String?): String {
        val t = raw?.trim()?.lowercase() ?: return DEFAULT
        return when (t) {
            "pasta", "nudeln" -> "pasta"
            "meat", "fleisch" -> "meat"
            "fish", "fisch" -> "fish"
            "soup", "suppe" -> "soup"
            "vegetarian", "vegetarisch", "veggie" -> "vegetarian"
            "salad", "salat" -> "salad"
            "dessert", "nachtisch" -> "dessert"
            "other", "sonstiges" -> "other"
            else -> if (t in KEYS) t else DEFAULT
        }
    }

    /** Display label: localized for known/legacy values; a custom free-text value is kept verbatim. */
    fun displayLabel(context: Context, raw: String?): String {
        if (raw.isNullOrBlank()) return context.getString(R.string.cat_other)
        val key = normalizeKey(raw)
        val isKnown = key != DEFAULT || raw.trim().lowercase() in listOf("other", "sonstiges")
        return if (isKnown) context.getString(labelRes(key)) else raw.trim()
    }
}

/**
 * Which meals a recipe suits (breakfast/lunch/snack/dinner) — the *when* axis,
 * deliberately separate from [RecipeCategories] (the *what* axis): a soup fits lunch
 * AND dinner, a Hefezopf is baked AND eaten at breakfast/coffee. Multi-value, stored
 * as language-independent keys in a newline-joined TEXT column (the `tags` pattern).
 *
 * `null`/blank storage means [DEFAULT] ("lunch + dinner") — evaluated at read time,
 * never backfilled: sync's MessageApplier rebuilds entities from log columns, so a
 * local-only backfill would be undone by the next sync apply.
 */
object MealTypes {
    /** Stable keys persisted in DB + sync log + AI prompt; order = order of the day. */
    val KEYS = listOf("breakfast", "lunch", "snack", "dinner")

    /** What an unset value means: the classic hot-meal slots. Uniform for all recipes
     *  (deliberately not category-aware) — baked goods are reclassified by hand. */
    val DEFAULT = listOf("lunch", "dinner")

    @StringRes
    fun labelRes(key: String): Int = when (key) {
        "breakfast" -> R.string.mealtype_breakfast
        "lunch" -> R.string.mealtype_lunch
        "snack" -> R.string.mealtype_snack
        else -> R.string.mealtype_dinner
    }

    /** Map an AI/import value (any language) to a stable key; unknown → null (drop, don't guess). */
    fun normalizeKey(raw: String?): String? {
        val t = raw?.trim()?.lowercase() ?: return null
        return when (t) {
            "breakfast", "frühstück", "fruehstueck", "morgens" -> "breakfast"
            "lunch", "mittag", "mittagessen" -> "lunch"
            "snack", "kaffee", "kuchen", "zwischenmahlzeit" -> "snack"
            "dinner", "abend", "abendessen", "abendbrot" -> "dinner"
            else -> if (t in KEYS) t else null
        }
    }

    /** Stored column → key list in [KEYS] order; null/blank → [DEFAULT]. */
    fun fromStored(stored: String?): List<String> {
        val keys = stored?.split("\n")?.mapNotNull { normalizeKey(it) }?.distinct().orEmpty()
        return keys.ifEmpty { DEFAULT }.let { list -> KEYS.filter { it in list } }
    }

    /** Key list → stored column; empty → null, so the default semantics apply again. */
    fun toStored(keys: List<String>): String? =
        KEYS.filter { it in keys }.takeIf { it.isNotEmpty() }?.joinToString("\n")
}
