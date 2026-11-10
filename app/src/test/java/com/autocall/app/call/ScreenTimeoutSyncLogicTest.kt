package com.autocall.app.call

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

class ScreenTimeoutSyncPlanTest {

    @Test
    fun planSync_activeSession_acquiresRegardlessOfHold() {
        assertEquals(
            ScreenTimeoutSyncLogic.Action.ACQUIRE,
            ScreenTimeoutSyncLogic.plan(
                hasActiveWatchSession = true,
                isHolding = false,
            ),
        )
        assertEquals(
            ScreenTimeoutSyncLogic.Action.ACQUIRE,
            ScreenTimeoutSyncLogic.plan(
                hasActiveWatchSession = true,
                isHolding = true,
            ),
        )
    }

    @Test
    fun planSync_orphanedHold_releases() {
        assertEquals(
            ScreenTimeoutSyncLogic.Action.RELEASE,
            ScreenTimeoutSyncLogic.plan(
                hasActiveWatchSession = false,
                isHolding = true,
            ),
        )
    }

    @Test
    fun planSync_idleAndNotHolding_isNone() {
        assertEquals(
            ScreenTimeoutSyncLogic.Action.NONE,
            ScreenTimeoutSyncLogic.plan(
                hasActiveWatchSession = false,
                isHolding = false,
            ),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenTimeoutHelperSyncTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ScreenTimeoutHoldStore.clearAll(context)
        setWriteSettingsAllowed(false)
    }

    @Test
    fun syncWithWatchSession_activeSession_keepsExistingHold() {
        ScreenTimeoutHoldStore.markHolding(context, 20_000)
        ScreenTimeoutHelper.syncWithWatchSession(context, hasActiveWatchSession = true)

        assertTrue(ScreenTimeoutHelper.isHolding(context))
        assertEquals(20_000, ScreenTimeoutHoldStore.originalTimeoutMs(context))
    }

    @Test
    fun syncWithWatchSession_noSession_keepsOrphanUntilWritable() {
        ScreenTimeoutHoldStore.markHolding(context, 20_000)
        ScreenTimeoutHelper.syncWithWatchSession(context, hasActiveWatchSession = false)

        // Without WRITE_SETTINGS, release must keep the original so restore can retry.
        assertTrue(ScreenTimeoutHelper.isHolding(context))
        assertEquals(20_000, ScreenTimeoutHoldStore.originalTimeoutMs(context))
    }

    @Test
    fun syncWithWatchSession_noSession_releasesOrphanWhenWritable() {
        setWriteSettingsAllowed(true)
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            ScreenTimeoutHelper.DIAL_TIMEOUT_MS,
        )
        ScreenTimeoutHoldStore.markHolding(context, 20_000)

        ScreenTimeoutHelper.syncWithWatchSession(context, hasActiveWatchSession = false)

        assertFalse(ScreenTimeoutHelper.isHolding(context))
        assertEquals(-1, ScreenTimeoutHoldStore.originalTimeoutMs(context))
        assertEquals(
            20_000,
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                -1,
            ),
        )
    }

    @Test
    fun syncWithWatchSession_noSession_andNotHolding_isNoOp() {
        ScreenTimeoutHelper.syncWithWatchSession(context, hasActiveWatchSession = false)
        assertFalse(ScreenTimeoutHelper.isHolding(context))
    }

    @Test
    fun release_keepsHold_whenCannotWriteSettings() {
        ScreenTimeoutHoldStore.markHolding(context, 45_000)
        ScreenTimeoutHelper.release(context)
        assertTrue(ScreenTimeoutHelper.isHolding(context))
        assertEquals(45_000, ScreenTimeoutHoldStore.originalTimeoutMs(context))
    }

    @Test
    fun release_clearsHold_afterSuccessfulRestore() {
        setWriteSettingsAllowed(true)
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            ScreenTimeoutHelper.DIAL_TIMEOUT_MS,
        )
        ScreenTimeoutHoldStore.markHolding(context, 45_000)

        ScreenTimeoutHelper.release(context)

        assertFalse(ScreenTimeoutHelper.isHolding(context))
        assertEquals(
            45_000,
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                -1,
            ),
        )
    }

    private fun setWriteSettingsAllowed(allowed: Boolean) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        Shadows.shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_WRITE_SETTINGS,
            Process.myUid(),
            context.packageName,
            if (allowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED,
        )
    }
}
