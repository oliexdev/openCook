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

    /** German default staples (also the unit-test baseline). */
    private val DEFAULT_ALL_DE: Set<String> = setOf(
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

    /** Active staple set. Swapped at runtime by `LocalizedLists` to the content language. */
    @Volatile
    var ALL: Set<String> = DEFAULT_ALL_DE

    /** Replace the staple data for the active content language (called by `LocalizedLists`). */
    fun setData(all: Set<String>, pantry: List<String>) {
        if (all.isNotEmpty()) ALL = all
        if (pantry.isNotEmpty()) DEFAULT_PANTRY = pantry
    }

    /** True if [name] is one of [ALL] (plural-aware). */
    fun isStaple(name: String): Boolean = IngredientMatch.containsLike(ALL, name)

    /**
     * Items that are seeded into a freshly-created household's pantry so the
     * user doesn't have to type every basic by hand. A subset of [ALL] — combo
     * forms ("salz und pfeffer"), tap-water ("wasser") and alternative
     * spellings of the same item are excluded so the pantry shows one clean
     * row per real product.
     */
    @Volatile
    var DEFAULT_PANTRY: List<String> = listOf(
        "Salz", "Pfeffer", "Olivenöl", "Butter", "Essig", "Zucker", "Mehl",
        "Gemüsebrühe", "Brühwürfel", "Senf", "Tomatenmark", "Sojasoße",
        "Paprikapulver", "Muskat", "Honig",
        "Backpulver", "Vanillezucker", "Trockenhefe",
    )
}
