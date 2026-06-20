package com.food.opencook.ui.mealplan

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.food.opencook.R
import com.food.opencook.ui.mealplan.MealPlanner.ReasonCode
import com.food.opencook.ui.mealplan.MealPlanner.ReasonContribution
import com.food.opencook.util.RecipeCategories

/**
 * Maps a [ReasonContribution] from the scorer to the user-facing German string. Stays
 * UI-side so the planner core can remain Android-free. Details that contain a list of
 * lower-cased ingredient names get title-cased for readability (the planner stores
 * everything lowercase to make plural-aware matching trivial).
 */
object MealPlanReasons {

    /** Resolve the visible chip/sheet text for one contribution. Falls back to an
     *  empty string for codes that carry no display value (none today, but future-proof). */
    fun text(context: Context, c: ReasonContribution): String {
        // Ingredient-name lists get title-cased; numeric/category details are localized below.
        val list = c.detail?.let(::titleCaseList).orEmpty()
        val days = c.detail?.toIntOrNull() ?: 0
        return when (c.code) {
            ReasonCode.WEEK_REUSE -> context.getString(R.string.mealplan_reason_week_reuse, list)
            ReasonCode.PANTRY_REUSE -> context.getString(R.string.mealplan_reason_pantry_reuse, list)
            ReasonCode.LIKED -> context.getString(R.string.mealplan_reason_liked)
            ReasonCode.QUICK_WEEKDAY -> context.getString(
                R.string.mealplan_reason_quick_weekday,
                context.getString(R.string.wizard_time_minutes, days),
            )
            ReasonCode.BIG_BATCH_LEFTOVER -> context.getString(R.string.mealplan_reason_big_batch_leftover)
            ReasonCode.NEW_INGREDIENTS_NEEDED -> context.getString(R.string.mealplan_reason_new_ingredients, list)
            ReasonCode.RECENTLY_PLANNED -> context.getString(
                R.string.mealplan_reason_recently_planned,
                context.getString(R.string.recipe_cooked_days_ago, days),
            )
            ReasonCode.RECENTLY_COOKED -> context.getString(
                R.string.mealplan_reason_recently_cooked,
                context.getString(R.string.recipe_cooked_days_ago, days),
            )
            ReasonCode.SAME_CATEGORY_NEIGHBOUR -> context.getString(
                R.string.mealplan_reason_same_category,
                RecipeCategories.displayLabel(context, c.detail),
            )
            ReasonCode.MONOTONY -> context.getString(R.string.mealplan_reason_monotony, list)
        }
    }

    @Composable
    fun text(c: ReasonContribution): String = text(LocalContext.current, c)

    /** A small Material icon per ReasonCode — same icon shows in both the chip and the
     *  "why?" sheet so the user learns the visual shorthand after a couple of weeks. */
    fun icon(code: ReasonCode): ImageVector = when (code) {
        ReasonCode.WEEK_REUSE -> Icons.Outlined.Recycling
        ReasonCode.PANTRY_REUSE -> Icons.Outlined.Inventory2
        ReasonCode.LIKED -> Icons.Filled.Favorite
        ReasonCode.QUICK_WEEKDAY -> Icons.Outlined.Bolt
        ReasonCode.BIG_BATCH_LEFTOVER -> Icons.Outlined.LocalDining
        ReasonCode.NEW_INGREDIENTS_NEEDED -> Icons.Outlined.ShoppingBasket
        ReasonCode.RECENTLY_PLANNED -> Icons.Outlined.History
        ReasonCode.RECENTLY_COOKED -> Icons.Outlined.Restaurant
        ReasonCode.SAME_CATEGORY_NEIGHBOUR -> Icons.Outlined.Tune
        ReasonCode.MONOTONY -> Icons.Outlined.Repeat
    }

    /** Pre-sorted lists for the details sheet: pluses on top, minuses below. */
    fun split(reasons: List<ReasonContribution>): Pair<List<ReasonContribution>, List<ReasonContribution>> {
        val pos = reasons.filter { it.weight > 0.0 || it.code == ReasonCode.BIG_BATCH_LEFTOVER }
            .sortedByDescending { it.weight }
        val neg = reasons.filter { it.weight < 0.0 }
            .sortedBy { it.weight } // most-negative first
        return pos to neg
    }

    /** Capitalise every comma-separated ingredient ("aubergine, champignons" → "Aubergine, Champignons")
     *  — the scorer keeps names lowercase to make plural-aware matching trivial. */
    private fun titleCaseList(raw: String): String {
        if (!raw.contains(',')) return raw.replaceFirstChar { it.uppercase() }
        return raw.split(',').joinToString(", ") { it.trim().replaceFirstChar { c -> c.uppercase() } }
    }
}
