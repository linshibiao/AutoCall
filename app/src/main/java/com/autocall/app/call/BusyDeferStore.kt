package com.autocall.app.call

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BusyDeferState(
    val scheduledCallId: Long,
    val dayOfWeek: Int,
    val retryAttempt: Int,
    val deferredSinceMillis: Long,
    val contactName: String?,
    val phoneNumber: String,
    val awaitingUserDecision: Boolean,
)

/**
 * Per-call busy-defer persistence. Multiple scheduled calls can be deferred at once without
 * clobbering each other.
 */
object BusyDeferStore {

    fun load(context: Context, scheduledCallId: Long): BusyDeferState? {
        val raw = prefs(context).getString(keyFor(scheduledCallId), null) ?: return null
        return decode(raw)
    }

    fun save(context: Context, state: BusyDeferState) {
        val prefs = prefs(context)
        val ids = loadIds(prefs).toMutableSet().apply { add(state.scheduledCallId) }
        prefs.edit()
            .putString(keyFor(state.scheduledCallId), encode(state))
            .putString(KEY_IDS, encodeIds(ids))
            .commit()
    }

    fun clear(context: Context, scheduledCallId: Long) {
        val prefs = prefs(context)
        val ids = loadIds(prefs).toMutableSet().apply { remove(scheduledCallId) }
        val editor = prefs.edit().remove(keyFor(scheduledCallId))
        if (ids.isEmpty()) {
            editor.remove(KEY_IDS)
        } else {
            editor.putString(KEY_IDS, encodeIds(ids))
        }
        editor.commit()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().commit()
    }

    private fun encode(state: BusyDeferState): String =
        JSONObject()
            .put("scheduledCallId", state.scheduledCallId)
            .put("dayOfWeek", state.dayOfWeek)
            .put("retryAttempt", state.retryAttempt)
            .put("deferredSinceMillis", state.deferredSinceMillis)
            .put("contactName", state.contactName)
            .put("phoneNumber", state.phoneNumber)
            .put("awaitingUserDecision", state.awaitingUserDecision)
            .toString()

    private fun decode(raw: String): BusyDeferState? = runCatching {
        val json = JSONObject(raw)
        BusyDeferState(
            scheduledCallId = json.getLong("scheduledCallId"),
            dayOfWeek = json.getInt("dayOfWeek"),
            retryAttempt = json.getInt("retryAttempt"),
            deferredSinceMillis = json.getLong("deferredSinceMillis"),
            contactName = if (!json.has("contactName") || json.isNull("contactName")) {
                null
            } else {
                json.getString("contactName")
            },
            phoneNumber = json.optString("phoneNumber", ""),
            awaitingUserDecision = json.optBoolean("awaitingUserDecision", false),
        )
    }.getOrNull()

    private fun loadIds(prefs: android.content.SharedPreferences): Set<Long> {
        val raw = prefs.getString(KEY_IDS, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    add(array.getLong(index))
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun encodeIds(ids: Set<Long>): String {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        return array.toString()
    }

    private fun keyFor(scheduledCallId: Long): String = "$KEY_STATE_PREFIX$scheduledCallId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "autocall_busy_defer"
    private const val KEY_IDS = "deferred_call_ids"
    private const val KEY_STATE_PREFIX = "state_"
}
