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

package com.food.opencook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved recipe (schema.org/Recipe superset).
 *
 * The primary key is a client-generated UUID (not autoincrement): stable IDs are
 * required by the Phase-2 per-field sync, and retrofitting them later is costly.
 * Child rows (ingredients, instructions, nutrition, images) live in their own
 * tables and are read together via [com.food.opencook.data.local.relation.RecipeWithDetails].
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val description: String? = null,
    val recipeYield: String? = null,
    /** Numeric servings the recipe makes (planner: big-batch detection, scaling). */
    val servings: Int? = null,
    /** AI-assigned coarse category (Pasta, Fleisch, …) driving meal-plan variety. */
    val category: String? = null,
    /** Free-text notes/tips (schema.org openCookNotes), newline-joined. */
    val notes: String? = null,
    /** AI-assigned search tags (openCookTags), newline-joined. */
    val tags: String? = null,
    /** ISO date this recipe was last cooked — household-wide, drives the planner's recency penalty. */
    val lastCookedAt: String? = null,
    /** Optional collection/cookbook name for grouping in the recipe list. */
    val cookbook: String? = null,
    val prepTime: String? = null,
    val cookTime: String? = null,
    val totalTime: String? = null,
    /** Groups recipes extracted from the same source photo (multi-recipe pages). */
    val sourcePhotoId: String? = null,
    val householdId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
