package com.food.opencook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    // v10: structured ingredient quantity + recipe servings/category + shopping
    // quantity/unit. No 9->10 migration on purpose — dev data is disposable
    // (recipes are regenerated), so fallbackToDestructiveMigration recreates it.
    // v11: meal_plan.pinned + meal_days (skip a day) for the auto-planner.
    // v12: shopping_items provenance (sourceRecipeId/sourceDate) for "not found".
    // v13: recipe tags + lastCookedAt; recipe_likes (per-member liked).
    // v14: product_cache (barcode -> name, local lookup cache).
    // v15: meal_plan.reasonsJson — synced score breakdown so any household device
    //      can explain "why this dish?" after the planner runs on a different device.
    // v16: meal_plan.cookedAt — per-day "confirmed cooked" date, drives the optional
    //      1-tap mark and the self-healing roll-forward of un-cooked planned dishes.
    // v17: shopping_items.manual (manual entry always shows, beats pantry cover) +
    //      sourceRecipeIds (all contributing dishes for the "needed for …" label).
    version = 17,
    exportSchema = false,
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

    companion object {
        /** v3 adds the free-text notes column; preserves existing recipes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN notes TEXT")
            }
        }

        /** v4 adds job progress stage + acknowledgement for the status strip. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN stage TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN acknowledgedAt INTEGER")
            }
        }

        /** v5 adds the append-only sync message log. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS messages (" +
                        "timestamp TEXT NOT NULL PRIMARY KEY, " +
                        "dataset TEXT NOT NULL, " +
                        "rowId TEXT NOT NULL, " +
                        "col_key TEXT NOT NULL, " +
                        "value TEXT NOT NULL, " +
                        "householdId TEXT, " +
                        "createdAt INTEGER NOT NULL)",
                )
            }
        }

        /** v6 adds the shopping list. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS shopping_items (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "text TEXT NOT NULL, " +
                        "quantity TEXT, " +
                        "checked INTEGER NOT NULL, " +
                        "position INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
            }
        }

        /** v7 adds the optional cookbook/collection name on recipes. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN cookbook TEXT")
            }
        }

        /** v8 adds the pantry (staples in stock). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pantry_items (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
            }
        }

        /** v9 adds the meal plan. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_plan (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "date TEXT NOT NULL, " +
                        "recipeId TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
            }
        }

        /** v11 adds pinned plan entries + the per-day skip flag (auto-planner). */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_plan ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_days (" +
                        "date TEXT NOT NULL PRIMARY KEY, " +
                        "skipped INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)",
                )
            }
        }

        /** v12 adds shopping-item provenance (which planned recipe + day produced it). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN sourceRecipeId TEXT")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN sourceDate TEXT")
            }
        }

        /** v13 adds AI search tags + a household-wide last-cooked date on recipes,
         *  and the per-member recipe_likes table. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN lastCookedAt TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recipe_likes (" +
                        "recipeId TEXT NOT NULL, " +
                        "nodeId TEXT NOT NULL, " +
                        "liked INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(recipeId, nodeId))",
                )
            }
        }

        /** v14 adds the local barcode → product-name cache. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS product_cache (" +
                        "barcode TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "brand TEXT, " +
                        "updatedAt INTEGER NOT NULL)",
                )
            }
        }

        /** v15 adds the synced score-breakdown JSON on meal-plan entries. */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_plan ADD COLUMN reasonsJson TEXT")
            }
        }

        /** v16 adds the per-day "confirmed cooked" date on meal-plan entries. */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_plan ADD COLUMN cookedAt TEXT")
            }
        }

        /** v17 adds the manual-entry flag (always-show, beats pantry cover) and the
         *  multi-recipe provenance set for the shopping line's "needed for …" label. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN manual INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shopping_items ADD COLUMN sourceRecipeIds TEXT")
            }
        }
    }
}
