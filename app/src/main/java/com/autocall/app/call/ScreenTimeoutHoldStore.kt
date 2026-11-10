package com.autocall.app.call

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists whether AutoCall is holding an elevated screen-off timeout and the
 * user's original value to restore later.
 *
 * Separated from [ScreenTimeoutHelper] so unit tests can exercise hold semantics
 * without mocking [android.provider.Settings.System].
 */
internal object ScreenTimeoutHoldStore {

    const val PREFS_NAME = "autocall_screen_timeout"
    const val KEY_ORIGINAL_TIMEOUT_MS = "original_timeout_ms"
    const val KEY_HOLDING = "holding"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isHolding(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HOLDING, false)

    fun originalTimeoutMs(context: Context): Int =
        prefs(context).getInt(KEY_ORIGINAL_TIMEOUT_MS, -1)

    /**
     * Records the original timeout the first time a hold is established.
     * @return true if this call newly established the hold.
     */
    fun markHolding(context: Context, originalTimeoutMs: Int): Boolean {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_HOLDING, false)) return false
        prefs.edit()
            .putInt(KEY_ORIGINAL_TIMEOUT_MS, originalTimeoutMs)
            .putBoolean(KEY_HOLDING, true)
            .commit()
        return true
    }

    /**
     * Clears the hold flag and saved original.
     * @return the previously saved original timeout ms, or -1 if none / not holding.
     */
    fun clearHolding(context: Context): Int {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_HOLDING, false)) return -1
        val original = prefs.getInt(KEY_ORIGINAL_TIMEOUT_MS, -1)
        prefs.edit()
            .remove(KEY_ORIGINAL_TIMEOUT_MS)
            .putBoolean(KEY_HOLDING, false)
            .commit()
        return original
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().commit()
    }
}
