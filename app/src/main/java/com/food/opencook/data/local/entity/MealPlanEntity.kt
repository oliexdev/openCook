package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A recipe planned for a given day. [date] is ISO "yyyy-MM-dd". Syncs like recipes. */
@Entity(tableName = "meal_plan")
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val date: String,
    val recipeId: String,
    /** Pinned entries survive auto-regeneration ("Woche vorschlagen" / re-roll). */
    val pinned: Boolean = false,
    /** Score breakdown that produced this pick, as a JSON-encoded
     *  `List<MealPlanner.ReasonContribution>`. Null for manually-added entries (the
     *  user picked the recipe themselves, no algorithmic reason). Syncs along with
     *  the plan so other household devices can also explain "why this dish?". */
    val reasonsJson: String? = null,
    /** ISO "yyyy-MM-dd" of the day this dish was confirmed cooked, or null if not
     *  confirmed. Per-entry (distinct from recipes.lastCookedAt, which is per-recipe):
     *  the same recipe can be planned on several days, and the self-healing roll-forward
     *  needs to know whether *this* day's meal actually happened. Syncs with the plan. */
    val cookedAt: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
