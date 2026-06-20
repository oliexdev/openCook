package com.food.opencook.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.food.opencook.data.local.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    // Checked items keep their position — only their styling changes in the UI.
    // Use "Abgehakte entfernen" to clear them; they are never auto-shifted.
    @Query("SELECT * FROM shopping_items ORDER BY position ASC, createdAt ASC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getById(id: String): ShoppingItemEntity?

    /** An open (unchecked) item with the same name, for consolidating duplicates. */
    @Query("SELECT * FROM shopping_items WHERE checked = 0 AND text = :text COLLATE NOCASE LIMIT 1")
    suspend fun findOpenByText(text: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE checked = 1")
    suspend fun getChecked(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAll(): List<ShoppingItemEntity>

    /** Open items uniquely attributed to one planned dish on one day. */
    @Query("SELECT * FROM shopping_items WHERE checked = 0 AND sourceRecipeId = :recipeId AND sourceDate = :date")
    suspend fun getBySource(recipeId: String, date: String): List<ShoppingItemEntity>

    /** All items (any state) for one planned dish on one day — used to move provenance when a dish is rescheduled. */
    @Query("SELECT * FROM shopping_items WHERE sourceRecipeId = :recipeId AND sourceDate = :date")
    suspend fun getAllBySource(recipeId: String, date: String): List<ShoppingItemEntity>

    /** Shopping items (any state) generated for one planned dish on one day. */
    @Query("SELECT COUNT(*) FROM shopping_items WHERE sourceRecipeId = :recipeId AND sourceDate = :date")
    suspend fun countBySource(recipeId: String, date: String): Int

    /** Still-open (unbought) items for one planned dish on one day. */
    @Query("SELECT COUNT(*) FROM shopping_items WHERE checked = 0 AND sourceRecipeId = :recipeId AND sourceDate = :date")
    suspend fun countOpenBySource(recipeId: String, date: String): Int

    /** Still-open items from a recipe, across all days — for cascade-removal when the recipe is deleted. */
    @Query("SELECT * FROM shopping_items WHERE checked = 0 AND sourceRecipeId = :recipeId")
    suspend fun getOpenByRecipe(recipeId: String): List<ShoppingItemEntity>

    /** Distinct item names — one source for the add-field autocomplete. */
    @Query("SELECT DISTINCT text FROM shopping_items")
    suspend fun distinctTexts(): List<String>

    @Upsert
    suspend fun upsert(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
