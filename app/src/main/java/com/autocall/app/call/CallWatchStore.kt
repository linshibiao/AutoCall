package com.autocall.app.call

import android.content.Context

enum class CallWatchPhase {
    WAITING_OFFHOOK,
    IN_CALL,
    RETRY_PENDING,
}

data class CallWatchSession(
    val scheduledCallId: Long,
    val dayOfWeek: Int,
    val retryAttempt: Int,
    val expectedDurationSeconds: Int,
    val phase: CallWatchPhase,
    val originalScheduleMillis: Long,
    val placedAtMillis: Long,
    val offHookAtMillis: Long?,
    val contactName: String?,
    val phoneNumber: String,
)

object CallWatchStore {

    fun load(context: Context): CallWatchSession? {
        val prefs = prefs(context)
        val id = prefs.getLong(KEY_CALL_ID, -1L)
        if (id < 0) return null
        val expected = prefs.getInt(KEY_EXPECTED_DURATION, 0)
        if (expected <= 0) return null
        val phase = runCatching {
            CallWatchPhase.valueOf(prefs.getString(KEY_PHASE, null).orEmpty())
        }.getOrNull() ?: return null

        return CallWatchSession(
            scheduledCallId = id,
            dayOfWeek = prefs.getInt(KEY_DAY_OF_WEEK, -1),
            retryAttempt = prefs.getInt(KEY_RETRY_ATTEMPT, 0),
            expectedDurationSeconds = expected,
            phase = phase,
            originalScheduleMillis = prefs.getLong(KEY_ORIGINAL_SCHEDULE, 0L)
                .takeIf { it > 0 }
                ?: prefs.getLong(KEY_PLACED_AT, 0L),
            placedAtMillis = prefs.getLong(KEY_PLACED_AT, 0L),
            offHookAtMillis = prefs.getLong(KEY_OFFHOOK_AT, -1L).takeIf { it >= 0 },
            contactName = prefs.getString(KEY_CONTACT_NAME, null),
            phoneNumber = prefs.getString(KEY_PHONE_NUMBER, "").orEmpty(),
        )
    }

    fun save(context: Context, session: CallWatchSession) {
        prefs(context).edit()
            .putLong(KEY_CALL_ID, session.scheduledCallId)
            .putInt(KEY_DAY_OF_WEEK, session.dayOfWeek)
            .putInt(KEY_RETRY_ATTEMPT, session.retryAttempt)
            .putInt(KEY_EXPECTED_DURATION, session.expectedDurationSeconds)
            .putString(KEY_PHASE, session.phase.name)
            .putLong(KEY_ORIGINAL_SCHEDULE, session.originalScheduleMillis)
            .putLong(KEY_PLACED_AT, session.placedAtMillis)
            .putLong(KEY_OFFHOOK_AT, session.offHookAtMillis ?: -1L)
            .putString(KEY_CONTACT_NAME, session.contactName)
            .putString(KEY_PHONE_NUMBER, session.phoneNumber)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "autocall_call_watch"
    private const val KEY_CALL_ID = "scheduled_call_id"
    private const val KEY_DAY_OF_WEEK = "day_of_week"
    private const val KEY_RETRY_ATTEMPT = "retry_attempt"
    private const val KEY_EXPECTED_DURATION = "expected_duration_seconds"
    private const val KEY_PHASE = "phase"
    private const val KEY_ORIGINAL_SCHEDULE = "original_schedule"
    private const val KEY_PLACED_AT = "placed_at"
    private const val KEY_OFFHOOK_AT = "offhook_at"
    private const val KEY_CONTACT_NAME = "contact_name"
    private const val KEY_PHONE_NUMBER = "phone_number"
}
