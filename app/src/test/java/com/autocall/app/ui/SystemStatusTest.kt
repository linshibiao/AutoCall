package com.autocall.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemStatusTest {

    @Test
    fun needsAttention_false_whenAllGreenIncludingWriteSettings() {
        assertFalse(
            SystemStatus(
                hasCallPhonePermission = true,
                hasReadPhoneStatePermission = true,
                hasReadContactsPermission = true,
                hasNotificationPermission = true,
                canScheduleExactAlarms = true,
                isIgnoringBatteryOptimizations = true,
                canWriteSettings = true,
            ).needsAttention,
        )
    }

    @Test
    fun needsAttention_true_whenWriteSettingsMissing_evenIfOtherFlagsOk() {
        assertTrue(
            SystemStatus(
                hasCallPhonePermission = true,
                hasReadPhoneStatePermission = true,
                hasReadContactsPermission = true,
                hasNotificationPermission = true,
                canScheduleExactAlarms = true,
                isIgnoringBatteryOptimizations = true,
                canWriteSettings = false,
            ).needsAttention,
        )
    }

    @Test
    fun needsAttention_true_whenRuntimePermissionMissing() {
        assertTrue(
            SystemStatus(
                hasCallPhonePermission = false,
                hasReadPhoneStatePermission = true,
                hasReadContactsPermission = true,
                canWriteSettings = true,
            ).needsAttention,
        )
    }

    @Test
    fun hasAllRuntimePermissions_ignoresWriteSettings() {
        val status = SystemStatus(
            hasCallPhonePermission = true,
            hasReadPhoneStatePermission = true,
            hasReadContactsPermission = true,
            hasNotificationPermission = true,
            canWriteSettings = false,
        )
        assertTrue(status.hasAllRuntimePermissions)
        assertTrue(status.needsAttention)
    }
}
