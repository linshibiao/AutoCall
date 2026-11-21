package com.autocall.app.data

import android.content.Context

data class RetrySettings(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryDeadlineMinutes: Int = DEFAULT_RETRY_DEADLINE_MINUTES,
    val busyDeferPromptMinutes: Int = DEFAULT_BUSY_DEFER_PROMPT_MINUTES,
    val retryDelaySeconds: Int = DEFAULT_RETRY_DELAY_SECONDS,
) {
    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_RETRY_DEADLINE_MINUTES = 10
        const val DEFAULT_BUSY_DEFER_PROMPT_MINUTES = 5
        const val DEFAULT_RETRY_DELAY_SECONDS = 10
        const val MIN_RETRIES = 0
        const val MAX_RETRIES_LIMIT = 10
        const val MIN_RETRY_DEADLINE_MINUTES = 1
        const val MAX_RETRY_DEADLINE_MINUTES = 180
        const val MIN_BUSY_DEFER_PROMPT_MINUTES = 1
        const val MAX_BUSY_DEFER_PROMPT_MINUTES = 180
        const val MIN_RETRY_DELAY_SECONDS = 1
        const val MAX_RETRY_DELAY_SECONDS = 120

        fun coerce(
            maxRetries: Int,
            retryDeadlineMinutes: Int,
            busyDeferPromptMinutes: Int,
            retryDelaySeconds: Int = DEFAULT_RETRY_DELAY_SECONDS,
        ): RetrySettings = RetrySettings(
            maxRetries = maxRetries.coerceIn(MIN_RETRIES, MAX_RETRIES_LIMIT),
            retryDeadlineMinutes = retryDeadlineMinutes.coerceIn(
                MIN_RETRY_DEADLINE_MINUTES,
                MAX_RETRY_DEADLINE_MINUTES,
            ),
            busyDeferPromptMinutes = busyDeferPromptMinutes.coerceIn(
                MIN_BUSY_DEFER_PROMPT_MINUTES,
                MAX_BUSY_DEFER_PROMPT_MINUTES,
            ),
            retryDelaySeconds = retryDelaySeconds.coerceIn(
                MIN_RETRY_DELAY_SECONDS,
                MAX_RETRY_DELAY_SECONDS,
            ),
        )
    }
}

class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun getRetrySettings(): RetrySettings = RetrySettings(
        maxRetries = prefs.getInt(
            KEY_MAX_RETRIES,
            RetrySettings.DEFAULT_MAX_RETRIES,
        ),
        retryDeadlineMinutes = prefs.getInt(
            KEY_RETRY_DEADLINE_MINUTES,
            RetrySettings.DEFAULT_RETRY_DEADLINE_MINUTES,
        ),
        busyDeferPromptMinutes = prefs.getInt(
            KEY_BUSY_DEFER_PROMPT_MINUTES,
            RetrySettings.DEFAULT_BUSY_DEFER_PROMPT_MINUTES,
        ),
        retryDelaySeconds = prefs.getInt(
            KEY_RETRY_DELAY_SECONDS,
            RetrySettings.DEFAULT_RETRY_DELAY_SECONDS,
        ),
    )

    fun setRetrySettings(
        maxRetries: Int,
        retryDeadlineMinutes: Int,
        busyDeferPromptMinutes: Int,
        retryDelaySeconds: Int,
    ): RetrySettings {
        val next = RetrySettings.coerce(
            maxRetries = maxRetries,
            retryDeadlineMinutes = retryDeadlineMinutes,
            busyDeferPromptMinutes = busyDeferPromptMinutes,
            retryDelaySeconds = retryDelaySeconds,
        )
        prefs.edit()
            .putInt(KEY_MAX_RETRIES, next.maxRetries)
            .putInt(KEY_RETRY_DEADLINE_MINUTES, next.retryDeadlineMinutes)
            .putInt(KEY_BUSY_DEFER_PROMPT_MINUTES, next.busyDeferPromptMinutes)
            .putInt(KEY_RETRY_DELAY_SECONDS, next.retryDelaySeconds)
            .apply()
        return next
    }

    companion object {
        private const val PREFS_NAME = "autocall_settings"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_RETRY_DEADLINE_MINUTES = "retry_deadline_minutes"
        private const val KEY_BUSY_DEFER_PROMPT_MINUTES = "busy_defer_prompt_minutes"
        private const val KEY_RETRY_DELAY_SECONDS = "retry_delay_seconds"
    }
}
