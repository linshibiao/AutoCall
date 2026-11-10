package com.autocall.app.call

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenTimeoutHoldStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ScreenTimeoutHoldStore.clearAll(context)
    }

    @Test
    fun isHolding_false_whenEmpty() {
        assertFalse(ScreenTimeoutHoldStore.isHolding(context))
        assertEquals(-1, ScreenTimeoutHoldStore.originalTimeoutMs(context))
    }

    @Test
    fun markHolding_savesOriginal_andSetsHolding() {
        assertTrue(ScreenTimeoutHoldStore.markHolding(context, 15_000))
        assertTrue(ScreenTimeoutHoldStore.isHolding(context))
        assertEquals(15_000, ScreenTimeoutHoldStore.originalTimeoutMs(context))
    }

    @Test
    fun markHolding_secondCall_doesNotOverwriteOriginal() {
        assertTrue(ScreenTimeoutHoldStore.markHolding(context, 15_000))
        assertFalse(ScreenTimeoutHoldStore.markHolding(context, 60_000))
        assertEquals(15_000, ScreenTimeoutHoldStore.originalTimeoutMs(context))
        assertTrue(ScreenTimeoutHoldStore.isHolding(context))
    }

    @Test
    fun clearHolding_returnsOriginal_andClearsFlag() {
        ScreenTimeoutHoldStore.markHolding(context, 30_000)
        assertEquals(30_000, ScreenTimeoutHoldStore.clearHolding(context))
        assertFalse(ScreenTimeoutHoldStore.isHolding(context))
        assertEquals(-1, ScreenTimeoutHoldStore.originalTimeoutMs(context))
    }

    @Test
    fun clearHolding_whenNotHolding_returnsMinusOne() {
        assertEquals(-1, ScreenTimeoutHoldStore.clearHolding(context))
        assertFalse(ScreenTimeoutHoldStore.isHolding(context))
    }

    @Test
    fun clearAll_wipesPersistedHold() {
        ScreenTimeoutHoldStore.markHolding(context, 12_000)
        ScreenTimeoutHoldStore.clearAll(context)
        assertFalse(ScreenTimeoutHoldStore.isHolding(context))
        assertEquals(-1, ScreenTimeoutHoldStore.originalTimeoutMs(context))
    }
}
