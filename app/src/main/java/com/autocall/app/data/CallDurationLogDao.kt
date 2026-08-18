package com.autocall.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDurationLogDao {

    @Query("SELECT * FROM call_duration_logs ORDER BY recordedAtMillis DESC")
    fun getAll(): Flow<List<CallDurationLog>>

    @Insert
    suspend fun insert(log: CallDurationLog): Long

    @Query(
        """
        SELECT id FROM call_duration_logs
        WHERE scheduledCallId = :scheduledCallId
        ORDER BY recordedAtMillis DESC, id DESC
        """,
    )
    suspend fun idsForCall(scheduledCallId: Long): List<Long>

    @Query("DELETE FROM call_duration_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM call_duration_logs WHERE scheduledCallId = :scheduledCallId")
    suspend fun deleteForCall(scheduledCallId: Long)
}
