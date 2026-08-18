package com.autocall.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.autocall.app.util.DaysOfWeek

@Entity(tableName = "scheduled_calls")
data class ScheduledCall(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String? = null,
    val phoneNumber: String,
    /** Comma-separated Calendar day-of-week values: 1 = Sunday … 7 = Saturday. */
    val daysOfWeek: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val useSpeakerphone: Boolean = false,
    /** Expected connected duration in seconds. Null or 0 means retries are disabled. */
    val expectedDurationSeconds: Int? = null,
) {
    fun days(): Set<Int> = DaysOfWeek.parse(daysOfWeek)

    fun hasExpectedDuration(): Boolean = (expectedDurationSeconds ?: 0) > 0
}
