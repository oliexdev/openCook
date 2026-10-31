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
    SNACKS(R.string.grocery_cat_snacks, "🍿"),
    SPICES(R.string.grocery_cat_spices, "🧂"),
    PANTRY(R.string.grocery_cat_pantry, "🥫"),
    HOUSEHOLD(R.string.grocery_cat_household, "🧴"),
    PETS(R.string.grocery_cat_pets, "🐾"),
    OTHER(R.string.grocery_cat_other, "🧺"),
}

/**
 * Maps an ingredient/product name to a grocery aisle by keyword (offline, German).
 * Best-effort and order-sensitive: more specific aisles are checked first (e.g. "Kokosmilch"
 * lands in PANTRY before the DAIRY "milch" rule, "Olivenöl" in SPICES). Unknown → OTHER.
 */
object GroceryCategories {

    // Checked in order; first category with a matching keyword (substring, case-insensitive) wins.
    // Non-food aisles and snacks go first: "Zahnpasta" must not land in PANTRY ("pasta"),
    // "Spülmaschinensalz" not in SPICES, "Kartoffelchips" not in PRODUCE.
    private val DEFAULT_RULES_DE: List<Pair<GroceryCategory, List<String>>> = listOf(
        GroceryCategory.HOUSEHOLD to listOf(
            "spülmittel", "waschmittel", "weichspüler", "spültabs", "spülmaschine", "putzmittel", "reiniger",
            "klopapier", "toilettenpapier", "küchenrolle", "taschentücher", "müllbeutel", "alufolie",
            "frischhaltefolie", "backpapier", "gefrierbeutel", "zahnpasta", "zahnbürste", "zahnseide",
            "shampoo", "duschgel", "seife", " deo", "rasier", "windel", "wattepads",
        ),
        GroceryCategory.PETS to listOf("futter", "katzenstreu", "leckerli", "kauknochen"),
        GroceryCategory.SNACKS to listOf(
            "chips", "flips", "salzstangen", "gummibärchen", "süßigkeit", "praline", "knabber", "popcorn",
            "keks", "riegel",
        ),
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

    /** Active rules — exposed so tests can snapshot and restore around [setRules]. */
    val activeRules: List<Pair<GroceryCategory, List<String>>> get() = rules

    fun categorize(name: String): GroceryCategory {
        val n = " ${TextFold.forMatch(name).trim()} "
        for ((category, keywords) in rules) {
            if (keywords.any { n.contains(it) }) return category
        }
        return GroceryCategory.OTHER
    }

    /**
     * Like [categorize], but a household-taught override (normalized name → aisle,
     * from [com.food.opencook.repository.GroceryOverrideRepository]) beats the
     * keyword heuristic — the user knows their supermarket better than our rules.
     *
     * A lesson also covers variants that contain it as whole words: "Zahnpasta" moved to
     * HOUSEHOLD takes "Zahnpasta sensitiv" along, while "Salz" never reaches "Salzstangen".
     * The exact name wins, then the longest contained lesson — so moving a variant makes
     * it the strongest match and corrects an over-eager generalization.
     */
    fun categorize(name: String, overrides: Map<String, GroceryCategory>): GroceryCategory {
        if (overrides.isEmpty()) return categorize(name)
        val key = name.trim().lowercase()
        overrides[key]?.let { return it }
        val tokens = words(key)
        if (tokens.size > 1) {
            lessonsOf(overrides)
                .firstOrNull { (lesson, _) -> lesson.size < tokens.size && tokens.containsRun(lesson) }
                ?.let { return it.second }
        }
        return categorize(name)
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Words folded like the keyword lists ([TextFold.forMatch]): Arabic "معجون الأسنان" and
     *  "معجون اسنان" yield the same words, so a lesson carries across spelling variants. */
    private fun words(s: String): List<String> =
        s.split(NON_WORD).map(TextFold::forMatch).filter { it.isNotEmpty() }

    private fun List<String>.containsRun(run: List<String>): Boolean =
        (0..size - run.size).any { start -> run.indices.all { this[start + it] == run[it] } }

    /** Tokenized lessons, longest first; cached per overrides map — categorize runs per row. */
    @Volatile
    private var lessonCache: Pair<Map<String, GroceryCategory>, List<Pair<List<String>, GroceryCategory>>>? = null

    private fun lessonsOf(overrides: Map<String, GroceryCategory>): List<Pair<List<String>, GroceryCategory>> {
        lessonCache?.takeIf { it.first === overrides }?.let { return it.second }
        val lessons = overrides.map { (k, v) -> words(k) to v }
            .filter { it.first.isNotEmpty() }
            .sortedWith(compareByDescending<Pair<List<String>, GroceryCategory>> { it.first.size }
                .thenByDescending { it.first.sumOf(String::length) })
        lessonCache = overrides to lessons
        return lessons
    }

    /** Grouped items as aisle sections in display order. With [includeEmpty] (while dragging)
     *  every aisle is listed, empty ones too, so an item can be moved into an aisle that has
     *  nothing in it yet (GitHub #41). */
    fun <T> sections(
        grouped: Map<GroceryCategory, List<T>>,
        includeEmpty: Boolean,
    ): List<Pair<GroceryCategory, List<T>>> =
        GroceryCategory.entries.mapNotNull { c ->
            (grouped[c] ?: emptyList<T>().takeIf { includeEmpty })?.let { c to it }
        }
}
