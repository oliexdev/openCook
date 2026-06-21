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
