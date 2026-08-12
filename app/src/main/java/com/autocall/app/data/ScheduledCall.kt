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
) {
    fun days(): Set<Int> = DaysOfWeek.parse(daysOfWeek)
}
