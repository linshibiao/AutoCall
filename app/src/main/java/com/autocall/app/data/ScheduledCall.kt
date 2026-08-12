package com.autocall.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_calls")
data class ScheduledCall(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String? = null,
    val phoneNumber: String,
    /** Calendar day-of-week: 1 = Sunday … 7 = Saturday (matches [java.util.Calendar]). */
    val dayOfWeek: Int,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val useSpeakerphone: Boolean = false,
)
