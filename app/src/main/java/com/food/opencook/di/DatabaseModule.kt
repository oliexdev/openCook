package com.food.opencook.di

import android.content.Context
import androidx.room.Room
import com.food.opencook.data.local.OpenCookDatabase
import com.food.opencook.data.local.RoomTransactor
import com.food.opencook.data.local.Transactor
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OpenCookDatabase =
        Room.databaseBuilder(context, OpenCookDatabase::class.java, "opencook.db")
            .addMigrations(
                OpenCookDatabase.MIGRATION_2_3,
                OpenCookDatabase.MIGRATION_3_4,
                OpenCookDatabase.MIGRATION_4_5,
                OpenCookDatabase.MIGRATION_5_6,
                OpenCookDatabase.MIGRATION_6_7,
                OpenCookDatabase.MIGRATION_7_8,
                OpenCookDatabase.MIGRATION_8_9,
                OpenCookDatabase.MIGRATION_10_11,
                OpenCookDatabase.MIGRATION_11_12,
                OpenCookDatabase.MIGRATION_12_13,
                OpenCookDatabase.MIGRATION_13_14,
                OpenCookDatabase.MIGRATION_14_15,
                OpenCookDatabase.MIGRATION_15_16,
                OpenCookDatabase.MIGRATION_16_17,
            )
            // Fallback for any gap without an explicit migration (dev convenience).
            // Switch to exportSchema=true before the first real release.
            .fallbackToDestructiveMigration(dropAllTables = true)
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
}
