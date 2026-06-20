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
