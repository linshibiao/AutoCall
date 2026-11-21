package com.autocall.app.call

/**
 * Pure helpers for scheduling auto-hangup after a connected call.
 */
object EndCallTimerLogic {

    /**
     * Milliseconds until auto-hangup should fire, based on off-hook start and configured
     * end-call-after. Returns 0 if already due; null if end-call-after is unset/invalid.
     */
    fun remainingDelayMs(
        endCallAfterSeconds: Int,
        offHookAtMillis: Long,
        nowMillis: Long,
    ): Long? {
        if (endCallAfterSeconds <= 0 || offHookAtMillis <= 0) return null
        val totalMs = endCallAfterSeconds * 1_000L
        return (totalMs - (nowMillis - offHookAtMillis)).coerceAtLeast(0L)
    }
}
