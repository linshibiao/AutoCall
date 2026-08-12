package com.autocall.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledCallDao {

    @Query("SELECT * FROM scheduled_calls ORDER BY daysOfWeek, hour, minute")
    fun getAll(): Flow<List<ScheduledCall>>

    @Query("SELECT * FROM scheduled_calls WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduledCall?

    @Query("SELECT * FROM scheduled_calls WHERE isEnabled = 1")
    suspend fun getAllEnabled(): List<ScheduledCall>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scheduledCall: ScheduledCall): Long

    @Update
    suspend fun update(scheduledCall: ScheduledCall)

    @Delete
    suspend fun delete(scheduledCall: ScheduledCall)

    @Query("DELETE FROM scheduled_calls WHERE id = :id")
    suspend fun deleteById(id: Long)
}
