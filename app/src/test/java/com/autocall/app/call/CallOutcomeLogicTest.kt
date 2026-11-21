package com.autocall.app.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallOutcomeLogicTest {

    @Test
    fun isSuccessful_true_whenWithinWindow() {
        assertTrue(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 65,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
                endedByAutoHangup = false,
            ),
        )
    }

    @Test
    fun isSuccessful_false_whenOutsideWindow() {
        assertFalse(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 71,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
                endedByAutoHangup = false,
            ),
        )
    }

    @Test
    fun isSuccessful_false_whenAutoHangup_evenIfWithinWindow() {
        assertFalse(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 70,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
                endedByAutoHangup = true,
            ),
        )
    }

    @Test
    fun isSuccessful_true_whenNoExpectedDuration_evenIfAutoHangup() {
        assertTrue(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 30,
                expectedDurationSeconds = 0,
                successWindowSeconds = 10,
                endedByAutoHangup = true,
            ),
        )
    }

    @Test
    fun isSuccessful_true_atExactWindowBoundary() {
        assertTrue(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 70,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
                endedByAutoHangup = false,
            ),
        )
        assertTrue(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 50,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
                endedByAutoHangup = false,
            ),
        )
    }

    @Test
    fun isSuccessful_false_whenAutoHangup_atExactWindowBoundary() {
        assertFalse(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 70,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
                endedByAutoHangup = true,
            ),
        )
    }

    @Test
    fun isSuccessful_false_whenTooShort_withoutAutoHangup() {
        assertFalse(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 49,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
                endedByAutoHangup = false,
            ),
        )
    }

    @Test
    fun isSuccessful_true_whenExpectedNegative_evenIfAutoHangup() {
        assertTrue(
            CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = 5,
                expectedDurationSeconds = -1,
                successWindowSeconds = 0,
                endedByAutoHangup = true,
            ),
        )
    }
}
