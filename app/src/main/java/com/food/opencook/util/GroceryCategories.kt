package com.food.opencook.util

/** Supermarket-aisle categories for grouping the shopping list (with an emoji + display order). */
enum class GroceryCategory(val displayName: String, val emoji: String) {
    PRODUCE("Obst & Gemüse", "🥕"),
    MEAT_FISH("Fleisch & Fisch", "🥩"),
    DAIRY("Milchprodukte & Eier", "🧀"),
    BAKERY("Brot & Backwaren", "🍞"),
    FROZEN("Tiefkühl", "❄️"),
    DRINKS("Getränke", "🥤"),
    SPICES("Gewürze, Öl & Saucen", "🧂"),
    PANTRY("Vorrat & Trocken", "🥫"),
    OTHER("Sonstiges", "🧺"),
}

/**
 * Maps an ingredient/product name to a grocery aisle by keyword (offline, German).
 * Best-effort and order-sensitive: more specific aisles are checked first (e.g. "Kokosmilch"
 * lands in PANTRY before the DAIRY "milch" rule, "Olivenöl" in SPICES). Unknown → OTHER.
 */
object GroceryCategories {

    // Checked in order; first category with a matching keyword (substring, case-insensitive) wins.
    private val rules: List<Pair<GroceryCategory, List<String>>> = listOf(
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

    fun categorize(name: String): GroceryCategory {
        val n = " ${name.lowercase().trim()} "
        for ((category, keywords) in rules) {
            if (keywords.any { n.contains(it) }) return category
        }
        return GroceryCategory.OTHER
    }
}
