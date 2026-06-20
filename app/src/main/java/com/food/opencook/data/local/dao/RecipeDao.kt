package com.food.opencook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.food.opencook.data.local.entity.ImageEntity
import com.food.opencook.data.local.entity.IngredientEntity
import com.food.opencook.data.local.entity.InstructionEntity
import com.food.opencook.data.local.entity.NutritionEntity
import com.food.opencook.data.local.entity.RecipeEntity
import com.food.opencook.data.local.relation.RecipeWithDetails
import kotlinx.coroutines.flow.Flow

/** Lightweight projection for duplicate detection (avoids loading full recipes). */
data class RecipeIdName(val id: String, val name: String?)

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: String): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getByIdOnce(id: String): RecipeWithDetails?

    /** All recipes with details, one shot — the meal-planner candidate pool. */
    @Transaction
    @Query("SELECT * FROM recipes")
    suspend fun getAllOnce(): List<RecipeWithDetails>

    @Query("SELECT id FROM ingredients WHERE recipeId = :recipeId")
    suspend fun ingredientIdsFor(recipeId: String): List<String>

    @Query("SELECT id FROM instructions WHERE recipeId = :recipeId")
    suspend fun instructionIdsFor(recipeId: String): List<String>

    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun deleteIngredientById(id: String)

    @Query("DELETE FROM instructions WHERE id = :id")
    suspend fun deleteInstructionById(id: String)

    /** Recipes extracted from one scan (sourcePhotoId == jobId); drives the review screen. */
    @Transaction
    @Query("SELECT * FROM recipes WHERE sourcePhotoId = :sourcePhotoId ORDER BY createdAt ASC")
    fun observeBySourcePhoto(sourcePhotoId: String): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE sourcePhotoId = :sourcePhotoId ORDER BY createdAt ASC")
    suspend fun getBySourcePhoto(sourcePhotoId: String): List<RecipeWithDetails>

    /** Recipes from finished scans the user hasn't acknowledged yet. */
    @Transaction
    @Query(
        "SELECT * FROM recipes WHERE sourcePhotoId IN " +
            "(SELECT jobId FROM jobs WHERE status = 'done' AND acknowledgedAt IS NULL) " +
            "ORDER BY createdAt ASC",
    )
    fun observeUnreviewed(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query(
        "SELECT * FROM recipes WHERE sourcePhotoId IN " +
            "(SELECT jobId FROM jobs WHERE status = 'done' AND acknowledgedAt IS NULL) " +
            "ORDER BY createdAt ASC",
    )
    suspend fun getUnreviewed(): List<RecipeWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstructions(instructions: List<InstructionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ImageEntity>)

    /** Locally-sourced images not yet on the server (e.g. bundle imports). The sync
     *  engine uploads these so they reach other devices and survive a reinstall. */
    @Query("SELECT * FROM images WHERE remoteName IS NULL AND localPath IS NOT NULL")
    suspend fun localOnlyImages(): List<ImageEntity>

    /** Synced images we know by server name but haven't downloaded yet. The sync engine
     *  fetches each one and stores it so the recipe stays visible when the server is offline. */
    @Query("SELECT * FROM images WHERE remoteName IS NOT NULL AND localPath IS NULL")
    suspend fun remoteOnlyImages(): List<ImageEntity>

    @Query("UPDATE images SET remoteName = :name WHERE id = :id")
    suspend fun setImageRemoteName(id: String, name: String)

    @Query("UPDATE images SET localPath = :path WHERE id = :id")
    suspend fun setImageLocalPath(id: String, path: String)

    @Query("SELECT id FROM images WHERE recipeId = :recipeId")
    suspend fun imageIdsFor(recipeId: String): List<String>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getImageById(id: String): ImageEntity?

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteImageById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutrition(nutrition: NutritionEntity)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsFor(recipeId: String)

    @Query("DELETE FROM instructions WHERE recipeId = :recipeId")
    suspend fun deleteInstructionsFor(recipeId: String)

    @Query("DELETE FROM nutrition WHERE recipeId = :recipeId")
    suspend fun deleteNutritionFor(recipeId: String)

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM recipes WHERE id = :id)")
    suspend fun recipeExists(id: String): Boolean

    @Query("UPDATE recipes SET lastCookedAt = :dateIso, updatedAt = :now WHERE id = :recipeId")
    suspend fun setLastCookedAt(recipeId: String, dateIso: String?, now: Long)

    /** Distinct ingredient names — one source for the add-field autocomplete. */
    @Query("SELECT DISTINCT name FROM ingredients")
    suspend fun distinctIngredientNames(): List<String>

    /** id + name of every recipe — used for name-based duplicate detection (case/whitespace
     *  normalization happens in Kotlin so umlauts compare correctly, unlike SQLite lower()). */
    @Query("SELECT id, name FROM recipes")
    suspend fun allIdAndNames(): List<RecipeIdName>

    // Projection upserts: update-in-place (no REPLACE, so child rows aren't
    // cascade-deleted when a parent recipe is re-projected during sync).
    @Upsert suspend fun upsertRecipeEntity(recipe: RecipeEntity)
    @Upsert suspend fun upsertIngredientRow(ingredient: IngredientEntity)
    @Upsert suspend fun upsertInstructionRow(instruction: InstructionEntity)
    @Upsert suspend fun upsertNutritionRow(nutrition: NutritionEntity)
    @Upsert suspend fun upsertImageRow(image: ImageEntity)

    /**
     * Insert or fully replace a recipe and its child rows in one transaction.
     * Child rows are deleted first so reordered/removed list items don't linger.
     * Images are NOT touched here (managed separately as they download / change).
     */
    @Transaction
    suspend fun upsertRecipe(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        instructions: List<InstructionEntity>,
        nutrition: NutritionEntity?,
    ) {
        insertRecipe(recipe)
        deleteIngredientsFor(recipe.id)
        deleteInstructionsFor(recipe.id)
        deleteNutritionFor(recipe.id)
        insertIngredients(ingredients)
        insertInstructions(instructions)
        if (nutrition != null) insertNutrition(nutrition)
    }
}
