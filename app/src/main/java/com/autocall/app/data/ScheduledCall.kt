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
    /** Allowed ± seconds around expected duration. Used only when expected duration is set. */
    val successWindowSeconds: Int = DEFAULT_SUCCESS_WINDOW_SECONDS,
    /**
     * Auto-hangup after this many connected seconds. Null or 0 means do not force-end.
     * When expected duration is set, must be greater than expected + success window.
     */
    val endCallAfterSeconds: Int? = null,
) {
    fun days(): Set<Int> = DaysOfWeek.parse(daysOfWeek)

    fun hasExpectedDuration(): Boolean = (expectedDurationSeconds ?: 0) > 0

    fun hasEndCallAfter(): Boolean = (endCallAfterSeconds ?: 0) > 0

    companion object {
        const val DEFAULT_SUCCESS_WINDOW_SECONDS = 10
        const val MIN_SUCCESS_WINDOW_SECONDS = 0
        const val MAX_SUCCESS_WINDOW_SECONDS = 120
        const val MIN_END_CALL_AFTER_SECONDS = 1
        const val MAX_END_CALL_AFTER_SECONDS = 60 * 60

        /**
         * Smallest allowed end-call-after when expected duration is set:
         * expected + success window + 1 (must be strictly larger).
         */
        fun minimumEndCallAfterSeconds(
            expectedDurationSeconds: Int?,
            successWindowSeconds: Int,
        ): Int? {
            val expected = expectedDurationSeconds?.takeIf { it > 0 } ?: return null
            val window = successWindowSeconds.coerceIn(
                MIN_SUCCESS_WINDOW_SECONDS,
                MAX_SUCCESS_WINDOW_SECONDS,
            )
            return expected + window + 1
        }

        fun isValidEndCallAfterSeconds(
            endCallAfterSeconds: Int?,
            expectedDurationSeconds: Int?,
            successWindowSeconds: Int,
        ): Boolean {
            if (endCallAfterSeconds == null) return true
            if (endCallAfterSeconds < MIN_END_CALL_AFTER_SECONDS ||
                endCallAfterSeconds > MAX_END_CALL_AFTER_SECONDS
            ) {
                return false
            }
            val minimum = minimumEndCallAfterSeconds(
                expectedDurationSeconds,
                successWindowSeconds,
            ) ?: return true
            return endCallAfterSeconds >= minimum
        }

        fun normalizeEndCallAfterSeconds(
            endCallAfterSeconds: Int?,
            expectedDurationSeconds: Int?,
            successWindowSeconds: Int,
        ): Int? {
            val raw = endCallAfterSeconds?.takeIf { it > 0 } ?: return null
            val coerced = raw.coerceIn(MIN_END_CALL_AFTER_SECONDS, MAX_END_CALL_AFTER_SECONDS)
            val minimum = minimumEndCallAfterSeconds(
                expectedDurationSeconds,
                successWindowSeconds,
            ) ?: return coerced
            if (minimum > MAX_END_CALL_AFTER_SECONDS) return null
            return maxOf(coerced, minimum)
        }
    }
}
