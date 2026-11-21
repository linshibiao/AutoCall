package com.autocall.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndCallAfterValidationTest {

    @Test
    fun minimum_isNull_whenExpectedDurationUnset() {
        assertNull(ScheduledCall.minimumEndCallAfterSeconds(null, 10))
        assertNull(ScheduledCall.minimumEndCallAfterSeconds(0, 10))
    }

    @Test
    fun minimum_isExpectedPlusWindowPlusOne() {
        assertEquals(
            71,
            ScheduledCall.minimumEndCallAfterSeconds(
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
        assertEquals(
            61,
            ScheduledCall.minimumEndCallAfterSeconds(
                expectedDurationSeconds = 60,
                successWindowSeconds = 0,
            ),
        )
    }

    @Test
    fun isValid_allowsEmptyEndCallAfter() {
        assertTrue(
            ScheduledCall.isValidEndCallAfterSeconds(
                endCallAfterSeconds = null,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun isValid_allowsAnyPositive_whenExpectedUnset() {
        assertTrue(
            ScheduledCall.isValidEndCallAfterSeconds(
                endCallAfterSeconds = 5,
                expectedDurationSeconds = null,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun isValid_rejectsEqualToExpectedPlusWindow() {
        assertFalse(
            ScheduledCall.isValidEndCallAfterSeconds(
                endCallAfterSeconds = 70,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun isValid_acceptsStrictlyLargerThanExpectedPlusWindow() {
        assertTrue(
            ScheduledCall.isValidEndCallAfterSeconds(
                endCallAfterSeconds = 71,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun normalize_bumpsBelowMinimum_upToMinimum() {
        assertEquals(
            71,
            ScheduledCall.normalizeEndCallAfterSeconds(
                endCallAfterSeconds = 65,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun normalize_returnsNull_whenUnset() {
        assertNull(
            ScheduledCall.normalizeEndCallAfterSeconds(
                endCallAfterSeconds = null,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun isValid_rejectsZeroAndNegative() {
        assertFalse(
            ScheduledCall.isValidEndCallAfterSeconds(
                endCallAfterSeconds = 0,
                expectedDurationSeconds = null,
                successWindowSeconds = 10,
            ),
        )
        assertFalse(
            ScheduledCall.isValidEndCallAfterSeconds(
                endCallAfterSeconds = -5,
                expectedDurationSeconds = null,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun isValid_rejectsAboveMax() {
        assertFalse(
            ScheduledCall.isValidEndCallAfterSeconds(
                endCallAfterSeconds = ScheduledCall.MAX_END_CALL_AFTER_SECONDS + 1,
                expectedDurationSeconds = null,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun normalize_returnsNull_forZero() {
        assertNull(
            ScheduledCall.normalizeEndCallAfterSeconds(
                endCallAfterSeconds = 0,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun normalize_preservesValue_whenAlreadyAboveMinimum() {
        assertEquals(
            90,
            ScheduledCall.normalizeEndCallAfterSeconds(
                endCallAfterSeconds = 90,
                expectedDurationSeconds = 60,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun normalize_returnsNull_whenMinimumExceedsMax() {
        assertNull(
            ScheduledCall.normalizeEndCallAfterSeconds(
                endCallAfterSeconds = ScheduledCall.MAX_END_CALL_AFTER_SECONDS,
                expectedDurationSeconds = ScheduledCall.MAX_END_CALL_AFTER_SECONDS,
                successWindowSeconds = 10,
            ),
        )
    }

    @Test
    fun hasEndCallAfter_matchesPositiveOnly() {
        assertFalse(ScheduledCall(phoneNumber = "1", daysOfWeek = "2", hour = 9, minute = 0).hasEndCallAfter())
        assertFalse(
            ScheduledCall(
                phoneNumber = "1",
                daysOfWeek = "2",
                hour = 9,
                minute = 0,
                endCallAfterSeconds = 0,
            ).hasEndCallAfter(),
        )
        assertTrue(
            ScheduledCall(
                phoneNumber = "1",
                daysOfWeek = "2",
                hour = 9,
                minute = 0,
                endCallAfterSeconds = 71,
            ).hasEndCallAfter(),
        )
    }
}
