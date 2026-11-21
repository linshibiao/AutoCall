package com.autocall.app.call

import kotlin.math.abs

/**
 * Pure success/failure decisions for a watched call attempt.
 */
object CallOutcomeLogic {

    /**
     * True when this attempt counts as a success (no retry).
     *
     * Auto-hangup is always a failure when expected duration is configured, even if the
     * measured length still falls inside the success window (borderline timing).
     */
    fun isSuccessful(
        actualDurationSeconds: Int,
        expectedDurationSeconds: Int,
        successWindowSeconds: Int,
        endedByAutoHangup: Boolean,
    ): Boolean {
        if (expectedDurationSeconds <= 0) return true
        if (endedByAutoHangup) return false
        val delta = abs(actualDurationSeconds - expectedDurationSeconds)
        return delta <= successWindowSeconds
    }
}
