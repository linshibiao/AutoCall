package com.autocall.app.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCallFlagLogicTest {

    @Test
    fun treatAsTest_true_whenIntentFlagSet() {
        assertTrue(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = true,
                retryAttempt = 0,
                deferredSinceMillis = -1L,
                scheduledCallId = 1L,
                watchSession = null,
                busyDeferIsTestCall = null,
            ),
        )
    }

    @Test
    fun treatAsTest_true_onRetry_whenMatchingWatchSessionIsTest() {
        assertTrue(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 2,
                deferredSinceMillis = -1L,
                scheduledCallId = 7L,
                watchSession = TestCallFlagLogic.WatchTestSnapshot(
                    scheduledCallId = 7L,
                    isTestCall = true,
                ),
                busyDeferIsTestCall = null,
            ),
        )
    }

    @Test
    fun treatAsTest_false_onRetry_whenWatchSessionIsDifferentCall() {
        assertFalse(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 1,
                deferredSinceMillis = -1L,
                scheduledCallId = 7L,
                watchSession = TestCallFlagLogic.WatchTestSnapshot(
                    scheduledCallId = 99L,
                    isTestCall = true,
                ),
                busyDeferIsTestCall = null,
            ),
        )
    }

    @Test
    fun treatAsTest_false_onRetry_whenWatchSessionNotTest() {
        assertFalse(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 1,
                deferredSinceMillis = -1L,
                scheduledCallId = 7L,
                watchSession = TestCallFlagLogic.WatchTestSnapshot(
                    scheduledCallId = 7L,
                    isTestCall = false,
                ),
                busyDeferIsTestCall = null,
            ),
        )
    }

    @Test
    fun treatAsTest_false_onFirstAttempt_evenIfWatchSessionIsTest() {
        // First attempt must rely on the intent flag (or busy-defer), not a stale session.
        assertFalse(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 0,
                deferredSinceMillis = -1L,
                scheduledCallId = 7L,
                watchSession = TestCallFlagLogic.WatchTestSnapshot(
                    scheduledCallId = 7L,
                    isTestCall = true,
                ),
                busyDeferIsTestCall = null,
            ),
        )
    }

    @Test
    fun treatAsTest_true_whenBusyDeferMarksTest() {
        assertTrue(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 0,
                deferredSinceMillis = 1_000L,
                scheduledCallId = 3L,
                watchSession = null,
                busyDeferIsTestCall = true,
            ),
        )
    }

    @Test
    fun treatAsTest_false_whenBusyDeferPresentButNotTest() {
        assertFalse(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 0,
                deferredSinceMillis = 1_000L,
                scheduledCallId = 3L,
                watchSession = null,
                busyDeferIsTestCall = false,
            ),
        )
    }

    @Test
    fun treatAsTest_false_whenNotInBusyDefer_evenIfBusyDeferFlagWouldBeTrue() {
        assertFalse(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 0,
                deferredSinceMillis = -1L,
                scheduledCallId = 3L,
                watchSession = null,
                busyDeferIsTestCall = true,
            ),
        )
    }

    @Test
    fun treatAsTest_false_forNormalScheduledTrigger() {
        assertFalse(
            TestCallFlagLogic.treatAsTest(
                intentIsTestCall = false,
                retryAttempt = 0,
                deferredSinceMillis = -1L,
                scheduledCallId = 1L,
                watchSession = null,
                busyDeferIsTestCall = null,
            ),
        )
    }
}
