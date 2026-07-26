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
import com.food.opencook.data.local.entity.IngredientLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientLinkDao {

    @Upsert
    suspend fun upsert(link: IngredientLinkEntity)

    @Query("SELECT * FROM ingredient_links")
    fun observeAll(): Flow<List<IngredientLinkEntity>>

    @Query("SELECT * FROM ingredient_links")
    suspend fun getAll(): List<IngredientLinkEntity>

    @Query("DELETE FROM ingredient_links WHERE nameA = :nameA AND nameB = :nameB")
    suspend fun deleteByPair(nameA: String, nameB: String)
}
