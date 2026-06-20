package com.food.opencook.util

/**
 * Curated set of kitchen-background ingredients (Würzmittel, Vorrats-Standards,
 * Backbasis) that the meal-planner filters out before scoring ingredient reuse,
 * cardinality and pantry coverage. Without this filter "salt in Mon + Wed"
 * would score identically to "aubergine in Mon + Wed" — the reuse signal would
 * just measure how spice-rich a recipe is.
 *
 * Static and hardcoded on purpose:
 *  - **Not** auto-promoted from the user's pantry — a packet of spaghetti is
 *    available stock, not "always there".
 *  - **Not** auto-detected from per-recipe frequency — that would treat onion
 *    or garlic as staples just because they appear in many dishes, neutralising
 *    the main shoppability signal.
 *
 * Verified against `testfiles/recipes.json` (12 190 recipes): every entry is a
 * culinarily-neutral background condiment or staple. See plan
 * `~/.claude/plans/mellow-booping-kite.md` § B1.
 */
object IngredientStaples {

    /** Every staple spelling we recognise. Match is plural-aware via [IngredientMatch]. */
    val ALL: Set<String> = setOf(
        // Bundle A — the unstrittigen Klassiker
        "salz", "salz und pfeffer", "pfeffer", "schwarzer pfeffer", "weißer pfeffer",
        "öl", "olivenöl", "rapsöl", "sonnenblumenöl", "pflanzenöl",
        "butter", "wasser", "essig", "zucker", "mehl",
        // Bundle B — Würz- und Vorrats-Standards
        "brühe", "gemüsebrühe", "fleischbrühe", "hühnerbrühe", "brühwürfel",
        "senf", "tomatenmark",
        "sojasoße", "sojasauce",
        "paprikapulver", "muskat", "muskatnuss", "honig",
        // Bundle C — Backbasis
        "backpulver", "vanillezucker", "trockenhefe",
    )

    /** True if [name] is one of [ALL] (plural-aware). */
    fun isStaple(name: String): Boolean = IngredientMatch.containsLike(ALL, name)

    /**
     * Items that are seeded into a freshly-created household's pantry so the
     * user doesn't have to type every basic by hand. A subset of [ALL] — combo
     * forms ("salz und pfeffer"), tap-water ("wasser") and alternative
     * spellings of the same item are excluded so the pantry shows one clean
     * row per real product.
     */
    val DEFAULT_PANTRY: List<String> = listOf(
        "Salz", "Pfeffer", "Olivenöl", "Butter", "Essig", "Zucker", "Mehl",
        "Gemüsebrühe", "Brühwürfel", "Senf", "Tomatenmark", "Sojasoße",
        "Paprikapulver", "Muskat", "Honig",
        "Backpulver", "Vanillezucker", "Trockenhefe",
    )
}
