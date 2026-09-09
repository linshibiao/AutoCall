package com.autocall.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusyDeferLogicTest {

    @Test
    fun shouldDeferDial_whenLineBusy_andNotForcePlace() {
        assertTrue(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = true,
                forcePlace = false,
                activeSession = null,
                scheduledCallId = 1L,
                retryAttempt = 0,
            ),
        )
    }

    @Test
    fun shouldDeferDial_forcePlace_skipsLineBusy_whenNoSession() {
        assertFalse(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = true,
                forcePlace = true,
                activeSession = null,
                scheduledCallId = 1L,
                retryAttempt = 0,
            ),
        )
    }

    @Test
    fun shouldDeferDial_forcePlace_stillDefers_whenOtherWatchSessionActive() {
        assertTrue(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = true,
                forcePlace = true,
                activeSession = BusyDeferLogic.ActiveWatchSnapshot(
                    scheduledCallId = 99L,
                    phase = CallWatchPhase.IN_CALL,
                ),
                scheduledCallId = 1L,
                retryAttempt = 0,
            ),
        )
    }

    @Test
    fun shouldDeferDial_forcePlace_reclaimsSameCallSession() {
        assertFalse(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = true,
                forcePlace = true,
                activeSession = BusyDeferLogic.ActiveWatchSnapshot(
                    scheduledCallId = 1L,
                    phase = CallWatchPhase.IN_CALL,
                ),
                scheduledCallId = 1L,
                retryAttempt = 0,
            ),
        )
    }

    @Test
    fun shouldDeferDial_allowsSameCallRetry_whenRetryPendingAndLineIdle() {
        assertFalse(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = false,
                forcePlace = false,
                activeSession = BusyDeferLogic.ActiveWatchSnapshot(
                    scheduledCallId = 7L,
                    phase = CallWatchPhase.RETRY_PENDING,
                ),
                scheduledCallId = 7L,
                retryAttempt = 2,
            ),
        )
    }

    @Test
    fun shouldDeferDial_reclaimsStaleSameCall_whenLineIdle() {
        assertFalse(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = false,
                forcePlace = false,
                activeSession = BusyDeferLogic.ActiveWatchSnapshot(
                    scheduledCallId = 7L,
                    phase = CallWatchPhase.IN_CALL,
                ),
                scheduledCallId = 7L,
                retryAttempt = 0,
            ),
        )
    }

    @Test
    fun shouldDeferDial_blocksSameCall_whileLineStillBusy() {
        assertTrue(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = true,
                forcePlace = false,
                activeSession = BusyDeferLogic.ActiveWatchSnapshot(
                    scheduledCallId = 7L,
                    phase = CallWatchPhase.IN_CALL,
                ),
                scheduledCallId = 7L,
                retryAttempt = 1,
            ),
        )
    }

    @Test
    fun shouldDeferDial_blocksDifferentCall_evenWhenRetryPending() {
        assertTrue(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = false,
                forcePlace = false,
                activeSession = BusyDeferLogic.ActiveWatchSnapshot(
                    scheduledCallId = 1L,
                    phase = CallWatchPhase.RETRY_PENDING,
                ),
                scheduledCallId = 2L,
                retryAttempt = 1,
            ),
        )
    }

    @Test
    fun shouldDeferDial_idleLine_noSession_doesNotDefer() {
        assertFalse(
            BusyDeferLogic.shouldDeferDial(
                lineBusy = false,
                forcePlace = false,
                activeSession = null,
                scheduledCallId = 1L,
                retryAttempt = 0,
            ),
        )
    }

    @Test
    fun planBusyDefer_firstHit_showsDelayedAndPolls() {
        val now = 1_000_000L
        val plan = BusyDeferLogic.planBusyDefer(
            nowMillis = now,
            deferredSinceMillisFromIntent = -1L,
            existing = null,
            scheduledCallId = 42L,
            promptMinutes = 5,
        )
        assertEquals(BusyDeferLogic.DeferPhase.FIRST_DELAY_AND_POLL, plan.phase)
        assertEquals(now, plan.deferredSinceMillis)
        assertFalse(plan.awaitingUserDecision)
        assertTrue(plan.scheduleBusyPoll)
    }

    @Test
    fun planBusyDefer_continuePoll_preservesDeferredSince_andDoesNotConsumeRetrySemantics() {
        val deferredSince = 900_000L
        val existing = sampleState(
            deferredSinceMillis = deferredSince,
            awaitingUserDecision = false,
            retryAttempt = 0,
        )
        val plan = BusyDeferLogic.planBusyDefer(
            nowMillis = deferredSince + 30_000L,
            deferredSinceMillisFromIntent = deferredSince,
            existing = existing,
            scheduledCallId = 42L,
            promptMinutes = 5,
        )
        assertEquals(BusyDeferLogic.DeferPhase.CONTINUE_POLL, plan.phase)
        assertEquals(deferredSince, plan.deferredSinceMillis)
        assertTrue(plan.scheduleBusyPoll)
        assertFalse(plan.awaitingUserDecision)
    }

    @Test
    fun planBusyDefer_afterPromptWindow_showsDecisionAndStopsPolling() {
        val deferredSince = 1_000_000L
        val plan = BusyDeferLogic.planBusyDefer(
            nowMillis = deferredSince + 5 * 60_000L,
            deferredSinceMillisFromIntent = deferredSince,
            existing = sampleState(deferredSinceMillis = deferredSince),
            scheduledCallId = 42L,
            promptMinutes = 5,
        )
        assertEquals(BusyDeferLogic.DeferPhase.SHOW_DECISION_PROMPT, plan.phase)
        assertTrue(plan.awaitingUserDecision)
        assertFalse(plan.scheduleBusyPoll)
    }

    @Test
    fun planBusyDefer_awaitingUser_isNoop() {
        val deferredSince = 1_000_000L
        val awaiting = sampleState(
            deferredSinceMillis = deferredSince,
            awaitingUserDecision = true,
        )
        val noop = BusyDeferLogic.planBusyDefer(
            nowMillis = deferredSince + 10 * 60_000L,
            deferredSinceMillisFromIntent = deferredSince,
            existing = awaiting,
            scheduledCallId = 42L,
            promptMinutes = 5,
        )
        assertEquals(BusyDeferLogic.DeferPhase.AWAITING_USER, noop.phase)
        assertFalse(noop.scheduleBusyPoll)
    }

    @Test
    fun planBusyDefer_resumePolling_afterForceBlockedByOtherSession() {
        val deferredSince = 1_000_000L
        val awaiting = sampleState(
            deferredSinceMillis = deferredSince,
            awaitingUserDecision = true,
        )
        val plan = BusyDeferLogic.planBusyDefer(
            nowMillis = deferredSince + 10 * 60_000L,
            deferredSinceMillisFromIntent = deferredSince,
            existing = awaiting,
            scheduledCallId = 42L,
            promptMinutes = 5,
            resumePolling = true,
        )
        assertEquals(BusyDeferLogic.DeferPhase.CONTINUE_POLL, plan.phase)
        assertFalse(plan.awaitingUserDecision)
        assertTrue(plan.scheduleBusyPoll)
    }

    @Test
    fun planBusyDefer_ignoresExistingStateForDifferentCall() {
        val now = 2_000_000L
        val plan = BusyDeferLogic.planBusyDefer(
            nowMillis = now,
            deferredSinceMillisFromIntent = -1L,
            existing = sampleState(scheduledCallId = 99L),
            scheduledCallId = 42L,
            promptMinutes = 5,
        )
        assertEquals(BusyDeferLogic.DeferPhase.FIRST_DELAY_AND_POLL, plan.phase)
        assertEquals(now, plan.deferredSinceMillis)
    }

    @Test
    fun planBusyDefer_zeroPromptMinutes_coercedToOneMinuteWindow() {
        val deferredSince = 1_000_000L
        val before = BusyDeferLogic.planBusyDefer(
            nowMillis = deferredSince + 59_000L,
            deferredSinceMillisFromIntent = deferredSince,
            existing = sampleState(deferredSinceMillis = deferredSince),
            scheduledCallId = 42L,
            promptMinutes = 0,
        )
        assertEquals(BusyDeferLogic.DeferPhase.CONTINUE_POLL, before.phase)

        val after = BusyDeferLogic.planBusyDefer(
            nowMillis = deferredSince + 60_000L,
            deferredSinceMillisFromIntent = deferredSince,
            existing = sampleState(deferredSinceMillis = deferredSince),
            scheduledCallId = 42L,
            promptMinutes = 0,
        )
        assertEquals(BusyDeferLogic.DeferPhase.SHOW_DECISION_PROMPT, after.phase)
    }

    @Test
    fun isPastRetryDeadline_usesOriginalScheduleNotDeferTime() {
        val original = 1_000_000L
        assertFalse(
            BusyDeferLogic.isPastRetryDeadline(
                originalScheduleMillis = original,
                retryDeadlineMinutes = 10,
                atMillis = original + 9 * 60_000L,
            ),
        )
        assertTrue(
            BusyDeferLogic.isPastRetryDeadline(
                originalScheduleMillis = original,
                retryDeadlineMinutes = 10,
                atMillis = original + 10 * 60_000L,
            ),
        )
    }

    @Test
    fun displayName_prefersContactThenPhoneThenUnknown() {
        assertEquals("Ada", BusyDeferLogic.displayName("Ada", "555"))
        assertEquals("555", BusyDeferLogic.displayName("  ", "555"))
        assertEquals("unknown contact", BusyDeferLogic.displayName(null, ""))
    }

    private fun sampleState(
        scheduledCallId: Long = 42L,
        deferredSinceMillis: Long = 1_000_000L,
        awaitingUserDecision: Boolean = false,
        retryAttempt: Int = 0,
    ) = BusyDeferState(
        scheduledCallId = scheduledCallId,
        dayOfWeek = 2,
        retryAttempt = retryAttempt,
        deferredSinceMillis = deferredSinceMillis,
        contactName = "Ada",
        phoneNumber = "555-0100",
        awaitingUserDecision = awaitingUserDecision,
    )
}
