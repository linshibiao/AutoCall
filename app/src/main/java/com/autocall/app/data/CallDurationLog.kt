package com.autocall.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_duration_logs",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledCall::class,
            parentColumns = ["id"],
            childColumns = ["scheduledCallId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["scheduledCallId"])],
)
data class CallDurationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduledCallId: Long,
    val durationSeconds: Int,
    val recordedAtMillis: Long,
) {
    companion object {
        const val MAX_RECENT = 5
    }
}
