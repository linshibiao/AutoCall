package com.autocall.app.data

import android.content.Context

data class RetrySettings(
    val durationToleranceSeconds: Int = DEFAULT_TOLERANCE_SECONDS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val retryDeadlineMinutes: Int = DEFAULT_RETRY_DEADLINE_MINUTES,
) {
    companion object {
        const val DEFAULT_TOLERANCE_SECONDS = 10
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_RETRY_DEADLINE_MINUTES = 10
        const val MIN_TOLERANCE_SECONDS = 0
        const val MAX_TOLERANCE_SECONDS = 120
        const val MIN_RETRIES = 0
        const val MAX_RETRIES_LIMIT = 10
        const val MIN_RETRY_DEADLINE_MINUTES = 1
        const val MAX_RETRY_DEADLINE_MINUTES = 180
    }
}

class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun getRetrySettings(): RetrySettings = RetrySettings(
        durationToleranceSeconds = prefs.getInt(
            KEY_TOLERANCE,
            RetrySettings.DEFAULT_TOLERANCE_SECONDS,
        ),
        maxRetries = prefs.getInt(
            KEY_MAX_RETRIES,
            RetrySettings.DEFAULT_MAX_RETRIES,
        ),
        retryDeadlineMinutes = prefs.getInt(
            KEY_RETRY_DEADLINE_MINUTES,
            RetrySettings.DEFAULT_RETRY_DEADLINE_MINUTES,
        ),
    )

    fun setRetrySettings(
        toleranceSeconds: Int,
        maxRetries: Int,
        retryDeadlineMinutes: Int,
    ): RetrySettings {
        val next = RetrySettings(
            durationToleranceSeconds = toleranceSeconds.coerceIn(
                RetrySettings.MIN_TOLERANCE_SECONDS,
                RetrySettings.MAX_TOLERANCE_SECONDS,
            ),
            maxRetries = maxRetries.coerceIn(
                RetrySettings.MIN_RETRIES,
                RetrySettings.MAX_RETRIES_LIMIT,
            ),
            retryDeadlineMinutes = retryDeadlineMinutes.coerceIn(
                RetrySettings.MIN_RETRY_DEADLINE_MINUTES,
                RetrySettings.MAX_RETRY_DEADLINE_MINUTES,
            ),
        )
        prefs.edit()
            .putInt(KEY_TOLERANCE, next.durationToleranceSeconds)
            .putInt(KEY_MAX_RETRIES, next.maxRetries)
            .putInt(KEY_RETRY_DEADLINE_MINUTES, next.retryDeadlineMinutes)
            .apply()
        return next
    }

    companion object {
        private const val PREFS_NAME = "autocall_settings"
        private const val KEY_TOLERANCE = "duration_tolerance_seconds"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_RETRY_DEADLINE_MINUTES = "retry_deadline_minutes"
    }
}
