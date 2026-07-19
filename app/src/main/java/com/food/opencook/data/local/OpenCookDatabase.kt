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

package com.food.opencook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.food.opencook.data.local.dao.JobDao
import com.food.opencook.data.local.dao.MealDayDao
import com.food.opencook.data.local.dao.MealPlanDao
import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.dao.ProductCacheDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.dao.RecipeLikeDao
import com.food.opencook.data.local.dao.ShoppingDao
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.MealDayEntity
import com.food.opencook.data.local.entity.MealPlanEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.JobEntity
import com.food.opencook.data.local.entity.MessageEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.entity.RecipeLikeEntity
import com.food.opencook.data.local.entity.PantryItemEntity
import com.food.opencook.data.local.entity.ProductCacheEntity
import com.food.opencook.data.local.entity.ShoppingItemEntity

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        InstructionEntity::class,
        NutritionEntity::class,
        ImageEntity::class,
        JobEntity::class,
        MessageEntity::class,
        ShoppingItemEntity::class,
        PantryItemEntity::class,
        MealPlanEntity::class,
        MealDayEntity::class,
        RecipeLikeEntity::class,
        ProductCacheEntity::class,
    ],
    // v1: the final, collapsed schema for the first public release.
    // v2: added 'slot' (breakfast/lunch/dinner) to meal_plan table.
    version = 2,
    exportSchema = true,
)
abstract class OpenCookDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun jobDao(): JobDao
    abstract fun messageDao(): MessageDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun pantryDao(): PantryDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun mealDayDao(): MealDayDao
    abstract fun recipeLikeDao(): RecipeLikeDao
    abstract fun productCacheDao(): ProductCacheDao
}
