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
import com.food.opencook.data.local.entity.GroceryOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroceryOverrideDao {

    @Upsert
    suspend fun upsert(override: GroceryOverrideEntity)

    @Query("SELECT * FROM grocery_overrides")
    fun observeAll(): Flow<List<GroceryOverrideEntity>>

    @Query("SELECT * FROM grocery_overrides")
    suspend fun getAll(): List<GroceryOverrideEntity>

    @Query("DELETE FROM grocery_overrides WHERE name = :name")
    suspend fun deleteByName(name: String)
}
