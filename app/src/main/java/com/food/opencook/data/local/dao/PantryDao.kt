package com.food.opencook.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.food.opencook.data.local.entity.PantryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {

    @Query("SELECT * FROM pantry_items ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE id = :id")
    suspend fun getById(id: String): PantryItemEntity?

    @Query("SELECT * FROM pantry_items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PantryItemEntity?

    @Query("SELECT name FROM pantry_items")
    suspend fun allNames(): List<String>

    @Query("SELECT * FROM pantry_items")
    suspend fun getAll(): List<PantryItemEntity>

    @Upsert
    suspend fun upsert(item: PantryItemEntity)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
