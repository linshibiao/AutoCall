package com.autocall.app.call

/**
 * Pure busy-defer / dial-gate decisions shared by [CallTriggerService] and unit tests.
 */
object BusyDeferLogic {

    data class ActiveWatchSnapshot(
        val scheduledCallId: Long,
        val phase: CallWatchPhase,
    )

    /**
     * True when dialing now would interrupt another call or steal another call's watch session.
     *
     * - Another call's watch session always blocks (even with [forcePlace]).
     * - Telephony busy blocks unless [forcePlace].
     * - A same-call watch session does not block when the line is idle (or when forced), so
     *   stale/orphan sessions can be reclaimed.
     */
    fun shouldDeferDial(
        lineBusy: Boolean,
        forcePlace: Boolean,
        activeSession: ActiveWatchSnapshot?,
        scheduledCallId: Long,
        retryAttempt: Int,
    ): Boolean {
        val session = activeSession
        if (session != null && session.scheduledCallId != scheduledCallId) {
            return true
        }
        if (!forcePlace && lineBusy) return true
        return false
    }

    fun isPastRetryDeadline(
        originalScheduleMillis: Long,
        retryDeadlineMinutes: Int,
        atMillis: Long,
    ): Boolean {
        val deadlineMillis = originalScheduleMillis + retryDeadlineMinutes * 60_000L
        return atMillis >= deadlineMillis
    }

    enum class DeferPhase {
        /** Already showing Place/Skip; do nothing until the user acts. */
        AWAITING_USER,

        /** First time deferred: show "delayed" notification and keep polling. */
        FIRST_DELAY_AND_POLL,

        /** Still within the prompt window; keep polling without re-alerting. */
        CONTINUE_POLL,

        /** Prompt window elapsed: show Place/Skip and stop polling. */
        SHOW_DECISION_PROMPT,
    }

    data class DeferPlan(
        val phase: DeferPhase,
        val deferredSinceMillis: Long,
        val awaitingUserDecision: Boolean,
        /** When true, schedule another busy-poll alarm using the same retryAttempt. */
        val scheduleBusyPoll: Boolean,
    )

    /**
     * @param resumePolling when Place was tapped but another watch session still blocks —
     * keep polling instead of re-showing the decision prompt forever.
     */
    fun planBusyDefer(
        nowMillis: Long,
        deferredSinceMillisFromIntent: Long,
        existing: BusyDeferState?,
        scheduledCallId: Long,
        promptMinutes: Int,
        resumePolling: Boolean = false,
    ): DeferPlan {
        val matchingExisting = existing?.takeIf { it.scheduledCallId == scheduledCallId }

        if (resumePolling) {
            val deferredSince = when {
                deferredSinceMillisFromIntent > 0L -> deferredSinceMillisFromIntent
                matchingExisting != null -> matchingExisting.deferredSinceMillis
                else -> nowMillis
            }
            return DeferPlan(
                phase = if (matchingExisting == null && deferredSinceMillisFromIntent <= 0L) {
                    DeferPhase.FIRST_DELAY_AND_POLL
                } else {
                    DeferPhase.CONTINUE_POLL
                },
                deferredSinceMillis = deferredSince,
                awaitingUserDecision = false,
                scheduleBusyPoll = true,
            )
        }

        if (matchingExisting?.awaitingUserDecision == true) {
            return DeferPlan(
                phase = DeferPhase.AWAITING_USER,
                deferredSinceMillis = matchingExisting.deferredSinceMillis,
                awaitingUserDecision = true,
                scheduleBusyPoll = false,
            )
        }

        val deferredSince = when {
            deferredSinceMillisFromIntent > 0L -> deferredSinceMillisFromIntent
            matchingExisting != null -> matchingExisting.deferredSinceMillis
            else -> nowMillis
        }
        val isFirstDefer =
            matchingExisting == null && deferredSinceMillisFromIntent <= 0L
        val promptAfterMillis = promptMinutes.coerceAtLeast(1) * 60_000L

        if (nowMillis - deferredSince >= promptAfterMillis) {
            return DeferPlan(
                phase = DeferPhase.SHOW_DECISION_PROMPT,
                deferredSinceMillis = deferredSince,
                awaitingUserDecision = true,
                scheduleBusyPoll = false,
            )
        }

        if (isFirstDefer) {
            return DeferPlan(
                phase = DeferPhase.FIRST_DELAY_AND_POLL,
                deferredSinceMillis = deferredSince,
                awaitingUserDecision = false,
                scheduleBusyPoll = true,
            )
        }

        return DeferPlan(
            phase = DeferPhase.CONTINUE_POLL,
            deferredSinceMillis = deferredSince,
            awaitingUserDecision = false,
            scheduleBusyPoll = true,
        )
    }

    fun displayName(contactName: String?, phoneNumber: String): String =
        contactName?.takeIf { it.isNotBlank() }
            ?: phoneNumber.ifBlank { "unknown contact" }
}
