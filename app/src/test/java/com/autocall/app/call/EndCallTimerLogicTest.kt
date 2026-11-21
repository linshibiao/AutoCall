package com.autocall.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndCallTimerLogicTest {

    @Test
    fun remainingDelayMs_null_whenUnsetOrInvalid() {
        assertNull(EndCallTimerLogic.remainingDelayMs(0, 1_000L, 1_000L))
        assertNull(EndCallTimerLogic.remainingDelayMs(10, 0L, 1_000L))
        assertNull(EndCallTimerLogic.remainingDelayMs(-1, 1_000L, 1_000L))
    }

    @Test
    fun remainingDelayMs_fullDelay_atOffHook() {
        assertEquals(
            71_000L,
            EndCallTimerLogic.remainingDelayMs(
                endCallAfterSeconds = 71,
                offHookAtMillis = 5_000L,
                nowMillis = 5_000L,
            ),
        )
    }

    @Test
    fun remainingDelayMs_subtractsElapsed() {
        assertEquals(
            21_000L,
            EndCallTimerLogic.remainingDelayMs(
                endCallAfterSeconds = 71,
                offHookAtMillis = 5_000L,
                nowMillis = 55_000L,
            ),
        )
    }

    @Test
    fun remainingDelayMs_zero_whenAlreadyDue() {
        assertEquals(
            0L,
            EndCallTimerLogic.remainingDelayMs(
                endCallAfterSeconds = 71,
                offHookAtMillis = 5_000L,
                nowMillis = 5_000L + 80_000L,
            ),
        )
    }
}
