package com.autocall.app.call

/**
 * Pure resolution of whether a trigger should be treated as a dashboard test dial.
 *
 * Retries and busy-polls may arrive without [intentIsTestCall] when the flag was stored on
 * the watch session or busy-defer state instead.
 */
object TestCallFlagLogic {

    data class WatchTestSnapshot(
        val scheduledCallId: Long,
        val isTestCall: Boolean,
    )

    /**
     * @param intentIsTestCall flag from the triggering Intent / service start
     * @param retryAttempt current retry index (0 = first attempt)
     * @param deferredSinceMillis busy-defer epoch millis, or negative when not in a busy defer
     * @param watchSession active [CallWatchStore] snapshot, if any
     * @param busyDeferIsTestCall [BusyDeferStore] isTestCall for this call, if defer state exists
     */
    fun treatAsTest(
        intentIsTestCall: Boolean,
        retryAttempt: Int,
        deferredSinceMillis: Long,
        scheduledCallId: Long,
        watchSession: WatchTestSnapshot?,
        busyDeferIsTestCall: Boolean?,
    ): Boolean {
        if (intentIsTestCall) return true
        if (
            retryAttempt > 0 &&
            watchSession != null &&
            watchSession.scheduledCallId == scheduledCallId &&
            watchSession.isTestCall
        ) {
            return true
        }
        if (deferredSinceMillis >= 0L && busyDeferIsTestCall == true) {
            return true
        }
        return false
    }
}
