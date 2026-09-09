package com.autocall.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RetrySettingsCoerceTest {

    @Test
    fun coerce_usesDefaultsWhenWithinRange() {
        val settings = RetrySettings.coerce(
            maxRetries = 3,
            retryDeadlineMinutes = 10,
            busyDeferPromptMinutes = 5,
        )
        assertEquals(3, settings.maxRetries)
        assertEquals(10, settings.retryDeadlineMinutes)
        assertEquals(5, settings.busyDeferPromptMinutes)
    }

    @Test
    fun coerce_clampsBusyDeferPrompt_toMinAndMax() {
        assertEquals(
            RetrySettings.MIN_BUSY_DEFER_PROMPT_MINUTES,
            RetrySettings.coerce(3, 10, 0).busyDeferPromptMinutes,
        )
        assertEquals(
            RetrySettings.MAX_BUSY_DEFER_PROMPT_MINUTES,
            RetrySettings.coerce(3, 10, 999).busyDeferPromptMinutes,
        )
    }

    @Test
    fun coerce_clampsRetriesAndDeadline() {
        val low = RetrySettings.coerce(-5, 0, 5)
        assertEquals(RetrySettings.MIN_RETRIES, low.maxRetries)
        assertEquals(RetrySettings.MIN_RETRY_DEADLINE_MINUTES, low.retryDeadlineMinutes)

        val high = RetrySettings.coerce(100, 500, 5)
        assertEquals(RetrySettings.MAX_RETRIES_LIMIT, high.maxRetries)
        assertEquals(RetrySettings.MAX_RETRY_DEADLINE_MINUTES, high.retryDeadlineMinutes)
    }

    @Test
    fun defaults_matchProductExpectations() {
        assertEquals(3, RetrySettings.DEFAULT_MAX_RETRIES)
        assertEquals(10, RetrySettings.DEFAULT_RETRY_DEADLINE_MINUTES)
        assertEquals(5, RetrySettings.DEFAULT_BUSY_DEFER_PROMPT_MINUTES)
    }
}
