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

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a block inside a database transaction. Abstracted behind an interface so
 * repositories stay unit-testable (a fake just runs the block) while production
 * code gets real Room atomicity.
 */
interface Transactor {
    suspend fun <R> withTransaction(block: suspend () -> R): R
}

@Singleton
class RoomTransactor @Inject constructor(
    private val db: OpenCookDatabase,
) : Transactor {
    override suspend fun <R> withTransaction(block: suspend () -> R): R =
        db.withTransaction(block)
}
