package com.autocall.app.call

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BusyDeferStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BusyDeferStore.clearAll(context)
    }

    @Test
    fun load_returnsNull_whenEmpty() {
        assertNull(BusyDeferStore.load(context, 11L))
    }

    @Test
    fun save_and_load_roundTripsState() {
        val state = BusyDeferState(
            scheduledCallId = 11L,
            dayOfWeek = 3,
            retryAttempt = 2,
            deferredSinceMillis = 1_234_567L,
            contactName = "Bo",
            phoneNumber = "555-0199",
            awaitingUserDecision = true,
            isTestCall = true,
        )
        BusyDeferStore.save(context, state)
        assertEquals(state, BusyDeferStore.load(context, 11L))
    }

    @Test
    fun clear_removesOnlyMatchingCall() {
        val first = BusyDeferState(
            scheduledCallId = 1L,
            dayOfWeek = 1,
            retryAttempt = 0,
            deferredSinceMillis = 100L,
            contactName = "A",
            phoneNumber = "1",
            awaitingUserDecision = false,
        )
        val second = BusyDeferState(
            scheduledCallId = 2L,
            dayOfWeek = 2,
            retryAttempt = 1,
            deferredSinceMillis = 200L,
            contactName = "B",
            phoneNumber = "2",
            awaitingUserDecision = true,
        )
        BusyDeferStore.save(context, first)
        BusyDeferStore.save(context, second)

        BusyDeferStore.clear(context, 1L)

        assertNull(BusyDeferStore.load(context, 1L))
        assertEquals(second, BusyDeferStore.load(context, 2L))
    }

    @Test
    fun load_defaultsIsTestCallFalse_whenLegacyJsonOmitsIt() {
        val legacy = JSONObject()
            .put("scheduledCallId", 42L)
            .put("dayOfWeek", 4)
            .put("retryAttempt", 1)
            .put("deferredSinceMillis", 999L)
            .put("contactName", "Cy")
            .put("phoneNumber", "555-0000")
            .put("awaitingUserDecision", false)
            .toString()
        context.getSharedPreferences("autocall_busy_defer", Context.MODE_PRIVATE)
            .edit()
            .putString("state_42", legacy)
            .putString("deferred_call_ids", "[42]")
            .commit()

        val loaded = BusyDeferStore.load(context, 42L)!!
        assertEquals(42L, loaded.scheduledCallId)
        assertFalse(loaded.isTestCall)
    }

    @Test
    fun save_keepsIndependentStatePerCall() {
        val first = BusyDeferState(
            scheduledCallId = 1L,
            dayOfWeek = 1,
            retryAttempt = 0,
            deferredSinceMillis = 100L,
            contactName = "A",
            phoneNumber = "1",
            awaitingUserDecision = false,
        )
        val second = BusyDeferState(
            scheduledCallId = 2L,
            dayOfWeek = 2,
            retryAttempt = 1,
            deferredSinceMillis = 200L,
            contactName = null,
            phoneNumber = "2",
            awaitingUserDecision = true,
        )
        BusyDeferStore.save(context, first)
        BusyDeferStore.save(context, second)
        assertEquals(first, BusyDeferStore.load(context, 1L))
        assertEquals(second, BusyDeferStore.load(context, 2L))
    }
}
