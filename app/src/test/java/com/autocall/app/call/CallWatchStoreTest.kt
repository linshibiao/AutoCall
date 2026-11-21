package com.autocall.app.call

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CallWatchStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CallWatchStore.clear(context)
    }

    @Test
    fun load_returnsNull_whenEmpty() {
        assertNull(CallWatchStore.load(context))
    }

    @Test
    fun save_and_load_roundTripsEndCallFields() {
        val session = sampleSession(
            endCallAfterSeconds = 90,
            endedByAutoHangup = true,
        )
        CallWatchStore.save(context, session)
        assertEquals(session, CallWatchStore.load(context))
    }

    @Test
    fun save_and_load_roundTripsDefaults_whenEndCallUnset() {
        val session = sampleSession(
            endCallAfterSeconds = 0,
            endedByAutoHangup = false,
        )
        CallWatchStore.save(context, session)
        val loaded = CallWatchStore.load(context)!!
        assertEquals(0, loaded.endCallAfterSeconds)
        assertFalse(loaded.endedByAutoHangup)
    }

    @Test
    fun load_defaultsNewFields_whenLegacyPrefsOmitThem() {
        context.getSharedPreferences("autocall_call_watch", Context.MODE_PRIVATE)
            .edit()
            .putLong("scheduled_call_id", 7L)
            .putInt("day_of_week", 2)
            .putInt("retry_attempt", 1)
            .putInt("expected_duration_seconds", 60)
            .putString("phase", CallWatchPhase.IN_CALL.name)
            .putLong("original_schedule", 1_000L)
            .putLong("placed_at", 1_100L)
            .putLong("offhook_at", 1_200L)
            .putString("contact_name", "Ada")
            .putString("phone_number", "555-0100")
            .putBoolean("use_speakerphone", true)
            .putInt("success_window_seconds", 10)
            .commit()

        val loaded = CallWatchStore.load(context)!!
        assertEquals(0, loaded.endCallAfterSeconds)
        assertFalse(loaded.endedByAutoHangup)
        assertEquals(CallWatchPhase.IN_CALL, loaded.phase)
        assertEquals(60, loaded.expectedDurationSeconds)
    }

    @Test
    fun clear_removesSessionIncludingEndCallFlags() {
        CallWatchStore.save(
            context,
            sampleSession(endCallAfterSeconds = 75, endedByAutoHangup = true),
        )
        CallWatchStore.clear(context)
        assertNull(CallWatchStore.load(context))
    }

    @Test
    fun copy_canToggleEndedByAutoHangup_andPersist() {
        val base = sampleSession(endedByAutoHangup = false)
        CallWatchStore.save(context, base)
        CallWatchStore.save(context, base.copy(endedByAutoHangup = true))
        assertTrue(CallWatchStore.load(context)!!.endedByAutoHangup)

        CallWatchStore.save(context, base.copy(endedByAutoHangup = false))
        assertFalse(CallWatchStore.load(context)!!.endedByAutoHangup)
    }

    private fun sampleSession(
        endCallAfterSeconds: Int = 0,
        endedByAutoHangup: Boolean = false,
    ) = CallWatchSession(
        scheduledCallId = 42L,
        dayOfWeek = 3,
        retryAttempt = 0,
        expectedDurationSeconds = 60,
        phase = CallWatchPhase.IN_CALL,
        originalScheduleMillis = 1_000_000L,
        placedAtMillis = 1_000_100L,
        offHookAtMillis = 1_000_200L,
        contactName = "Ada",
        phoneNumber = "555-0100",
        useSpeakerphone = false,
        successWindowSeconds = 10,
        endCallAfterSeconds = endCallAfterSeconds,
        endedByAutoHangup = endedByAutoHangup,
    )
}
