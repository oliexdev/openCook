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

import androidx.annotation.StringRes
import com.food.opencook.R

/** Supermarket-aisle categories for grouping the shopping list (with an emoji + display order). */
enum class GroceryCategory(@StringRes val labelRes: Int, val emoji: String) {
    PRODUCE(R.string.grocery_cat_produce, "🥕"),
    MEAT_FISH(R.string.grocery_cat_meat_fish, "🥩"),
    DAIRY(R.string.grocery_cat_dairy, "🧀"),
    BAKERY(R.string.grocery_cat_bakery, "🍞"),
    FROZEN(R.string.grocery_cat_frozen, "❄️"),
    DRINKS(R.string.grocery_cat_drinks, "🥤"),
    SPICES(R.string.grocery_cat_spices, "🧂"),
    PANTRY(R.string.grocery_cat_pantry, "🥫"),
    OTHER(R.string.grocery_cat_other, "🧺"),
}

/**
 * Maps an ingredient/product name to a grocery aisle by keyword (offline, German).
 * Best-effort and order-sensitive: more specific aisles are checked first (e.g. "Kokosmilch"
 * lands in PANTRY before the DAIRY "milch" rule, "Olivenöl" in SPICES). Unknown → OTHER.
 */
object GroceryCategories {

    // Checked in order; first category with a matching keyword (substring, case-insensitive) wins.
    private val DEFAULT_RULES_DE: List<Pair<GroceryCategory, List<String>>> = listOf(
        GroceryCategory.FROZEN to listOf("tiefkühl", "tk ", "gefroren", " eis", "eiscreme"),
        GroceryCategory.MEAT_FISH to listOf(
            "hähnchen", "huhn", "pute", "hack", "rind", "schwein", "speck", "schinken", "wurst", "salami",
            "lachs", "thunfisch", "garnele", "scampi", "fisch", "filet", "steak", "lamm", "fleisch", "bacon",
        ),
        GroceryCategory.PRODUCE to listOf(
            "tomate", "paprika", "zwiebel", "knoblauch", "karotte", "möhre", "kartoffel", "salat", "gurke",
            "zucchini", "brokkoli", "blumenkohl", "spinat", "lauch", "champignon", "pilz", "apfel", "äpfel",
            "banane", "zitrone", "limette", "orange", "beere", "avocado", "ingwer", "chili", "petersilie",
            "basilikum", "schnittlauch", "bohne", "erbse", "mais", "kürbis", "aubergine", "sellerie", "rucola",
            "birne", "traube", "mango", "kräuter", "gemüse", "obst",
        ),
        GroceryCategory.BAKERY to listOf("brot", "brötchen", "toast", "baguette", "semmel", "croissant"),
        GroceryCategory.DRINKS to listOf("wasser", "saft", "kaffee", " tee", "cola", "limo", "bier", "wein", "smoothie"),
        GroceryCategory.SPICES to listOf(
            "salz", "pfeffer", "paprikapulver", "currypaste", "curry", "kreuzkümmel", "zimt", "muskat", "oregano",
            "thymian", "rosmarin", "sojasauce", "sojasoße", "essig", "senf", "ketchup", "mayo", "brühe",
            "tomatenmark", "vanille", "sambal", "worcester", "gewürz", "olivenöl", "öl", "sauce", "soße",
        ),
        GroceryCategory.PANTRY to listOf(
            "nudel", "spaghetti", "pasta", "reis", "mehl", "zucker", "linsen", "kichererbsen", "couscous",
            "haferflocken", "müsli", "konserve", "dose", "passierte tomaten", "kokosmilch", "backpulver", "hefe",
            "honig", "marmelade", "schokolade", "kakao", "polenta", "grieß", "öl ", "essig",
        ),
        GroceryCategory.DAIRY to listOf(
            "milch", "sahne", "butter", "joghurt", "quark", "schmand", "frischkäse", "käse", "eier", "ei ",
            "mozzarella", "parmesan", "feta", "crème", "creme", "margarine",
        ),
    )

    /** Active keyword rules. Swapped at runtime by `LocalizedLists` to the content language;
     *  defaults to German so unit tests (and a not-yet-initialized process) still work. */
    @Volatile
    private var rules: List<Pair<GroceryCategory, List<String>>> = DEFAULT_RULES_DE

    /** Replace the aisle keyword rules (called by `LocalizedLists` on language change). */
    fun setRules(newRules: List<Pair<GroceryCategory, List<String>>>) {
        if (newRules.isNotEmpty()) rules = newRules
    }

    fun categorize(name: String): GroceryCategory {
        val n = " ${name.lowercase().trim()} "
        for ((category, keywords) in rules) {
            if (keywords.any { n.contains(it) }) return category
        }
        return GroceryCategory.OTHER
    }
}
