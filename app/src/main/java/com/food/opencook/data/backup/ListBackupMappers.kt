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

package com.food.opencook.data.backup

import com.food.opencook.data.local.entity.MealDayEntity
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.data.local.entity.ShoppingItemEntity

/**
 * Straight one-to-one conversions between the list entities and their backup shape.
 * Ids and timestamps are carried through unchanged, which is what makes a restore an
 * upsert rather than a duplicate-producing insert.
 */

fun ShoppingItemEntity.toBackup() = ShoppingItemBackup(
    id = id,
    text = text,
    quantity = quantity,
    unit = unit,
    checked = checked,
    position = position,
    sourceRecipeId = sourceRecipeId,
    sourceDate = sourceDate,
    manual = manual,
    sourceRecipeIds = sourceRecipeIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ShoppingItemBackup.toEntity() = ShoppingItemEntity(
    id = id,
    text = text,
    quantity = quantity,
    unit = unit,
    checked = checked,
    position = position,
    sourceRecipeId = sourceRecipeId,
    sourceDate = sourceDate,
    manual = manual,
    sourceRecipeIds = sourceRecipeIds,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PantryItemEntity.toBackup() = PantryItemBackup(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PantryItemBackup.toEntity() = PantryItemEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MealPlanEntity.toBackup() = MealPlanEntryBackup(
    id = id,
    date = date,
    recipeId = recipeId,
    pinned = pinned,
    reasonsJson = reasonsJson,
    cookedAt = cookedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MealPlanEntryBackup.toEntity() = MealPlanEntity(
    id = id,
    date = date,
    recipeId = recipeId,
    pinned = pinned,
    reasonsJson = reasonsJson,
    cookedAt = cookedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MealDayEntity.toBackup() = MealDayBackup(
    date = date,
    skipped = skipped,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MealDayBackup.toEntity() = MealDayEntity(
    date = date,
    skipped = skipped,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
