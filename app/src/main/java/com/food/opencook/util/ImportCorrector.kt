package com.food.opencook.util

/**
 * Applies only the **high-confidence** (single 1-edit match) ingredient-name corrections
 * at import time, silently, before a scanned recipe is stored and synced. Lower-confidence
 * matches are left for the review screen to *suggest* (they need the user's choice).
 *
 * Implemented over the curated bundled vocabulary only (lexicon + CommonGroceries), never the
 * user's raw stored data, so a misread can't snap to another messy entry. A no-op identity
 * (`ImportCorrector { it }`) is used where correction isn't wanted (e.g. tests).
 */
fun interface ImportCorrector {
    fun correct(name: String): String
}
