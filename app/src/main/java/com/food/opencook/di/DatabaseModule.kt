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

package com.food.opencook.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.food.opencook.data.local.OpenCookDatabase
import com.food.opencook.data.local.RoomTransactor
import com.food.opencook.data.local.Transactor
import com.food.opencook.data.local.dao.GroceryOverrideDao
import com.food.opencook.data.local.dao.JobDao
import com.food.opencook.data.local.dao.MealDayDao
import com.food.opencook.data.local.dao.MealPlanDao
import com.food.opencook.data.local.dao.MessageDao
import com.food.opencook.data.local.dao.PantryDao
import com.food.opencook.data.local.dao.ProductCacheDao
import com.food.opencook.data.local.dao.RecipeDao
import com.food.opencook.data.local.dao.RecipeLikeDao
import com.food.opencook.data.local.dao.ShoppingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTransactor(impl: RoomTransactor): Transactor = impl

    /** v1 → v2: the mealTypes column (nullable — null means "lunch + dinner", resolved
     *  at read time by [com.food.opencook.util.MealTypes.fromStored], deliberately not
     *  backfilled; see the KDoc there for the sync rationale). */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recipes ADD COLUMN mealTypes TEXT")
        }
    }

    /** v2 → v3: the learned "name → aisle" override table (see GroceryOverrideEntity). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS grocery_overrides(" +
                    "name TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, updatedAt INTEGER NOT NULL)",
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenCookDatabase =
        // v1 is the released baseline schema (see OpenCookDatabase).
        Room.databaseBuilder(context, OpenCookDatabase::class.java, "opencook.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideRecipeDao(database: OpenCookDatabase): RecipeDao = database.recipeDao()

    @Provides
    fun provideJobDao(database: OpenCookDatabase): JobDao = database.jobDao()

    @Provides
    fun provideMessageDao(database: OpenCookDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideShoppingDao(database: OpenCookDatabase): ShoppingDao = database.shoppingDao()

    @Provides
    fun providePantryDao(database: OpenCookDatabase): PantryDao = database.pantryDao()

    @Provides
    fun provideMealPlanDao(database: OpenCookDatabase): MealPlanDao = database.mealPlanDao()

    @Provides
    fun provideMealDayDao(database: OpenCookDatabase): MealDayDao = database.mealDayDao()

    @Provides
    fun provideRecipeLikeDao(database: OpenCookDatabase): RecipeLikeDao = database.recipeLikeDao()

    @Provides
    fun provideProductCacheDao(database: OpenCookDatabase): ProductCacheDao = database.productCacheDao()

    @Provides
    fun provideGroceryOverrideDao(database: OpenCookDatabase): GroceryOverrideDao = database.groceryOverrideDao()
}
