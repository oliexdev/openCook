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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.food.opencook.data.local.entity.JobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Query("SELECT * FROM jobs WHERE jobId = :jobId")
    suspend fun getById(jobId: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE jobId = :jobId")
    fun observeById(jobId: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE status IN ('pending', 'processing') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status IN ('pending', 'processing')")
    suspend fun getActive(): List<JobEntity>

    @Query(
        "SELECT * FROM jobs WHERE status = 'done' AND resultDrainedAt IS NOT NULL " +
            "AND acknowledgedAt IS NULL ORDER BY updatedAt DESC",
    )
    fun observeFinishedUnacknowledged(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status = 'error' AND acknowledgedAt IS NULL ORDER BY updatedAt DESC")
    fun observeFailedUnacknowledged(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status = 'error' AND acknowledgedAt IS NULL")
    suspend fun getFailedUnacknowledged(): List<JobEntity>

    /** Mark all terminal (done or failed) scans as seen, so the strip clears. */
    @Query("UPDATE jobs SET acknowledgedAt = :ts WHERE status IN ('done', 'error') AND acknowledgedAt IS NULL")
    suspend fun acknowledgeAllFinished(ts: Long)

    @Query("DELETE FROM jobs WHERE jobId = :jobId")
    suspend fun deleteById(jobId: String)

    @Query("UPDATE jobs SET status = :status, error = :error, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateStatus(jobId: String, status: String, error: String?, updatedAt: Long)

    @Query("UPDATE jobs SET status = :status, stage = :stage, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun updateProgress(jobId: String, status: String, stage: String?, updatedAt: Long)

    @Query("UPDATE jobs SET serverJobId = :serverJobId, status = :status, updatedAt = :updatedAt WHERE jobId = :jobId")
    suspend fun setServerJob(jobId: String, serverJobId: String, status: String, updatedAt: Long)

    @Query("UPDATE jobs SET resultDrainedAt = :ts, updatedAt = :ts WHERE jobId = :jobId")
    suspend fun markDrained(jobId: String, ts: Long)
}
