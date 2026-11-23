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
                hasAnswerPhoneCallsPermission = true,
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
                hasAnswerPhoneCallsPermission = true,
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
                hasAnswerPhoneCallsPermission = true,
                hasReadPhoneStatePermission = true,
                hasReadContactsPermission = true,
                canWriteSettings = true,
            ).needsAttention,
        )
    }

    @Test
    fun needsAttention_true_whenAnswerPhoneCallsMissing() {
        assertTrue(
            SystemStatus(
                hasCallPhonePermission = true,
                hasAnswerPhoneCallsPermission = false,
                hasReadPhoneStatePermission = true,
                hasReadContactsPermission = true,
                hasNotificationPermission = true,
                canWriteSettings = true,
            ).needsAttention,
        )
    }

    @Test
    fun hasAllRuntimePermissions_ignoresWriteSettings() {
        val status = SystemStatus(
            hasCallPhonePermission = true,
            hasAnswerPhoneCallsPermission = true,
            hasReadPhoneStatePermission = true,
            hasReadContactsPermission = true,
            hasNotificationPermission = true,
            canWriteSettings = false,
        )
        assertTrue(status.hasAllRuntimePermissions)
        assertTrue(status.needsAttention)
    }

    @Test
    fun needsCallRedirectionRole_neverRequired_whenGatewayBypassUsed() {
        val status = SystemStatus(
            hasCallPhonePermission = true,
            hasAnswerPhoneCallsPermission = true,
            hasReadPhoneStatePermission = true,
            hasReadContactsPermission = true,
            hasNotificationPermission = true,
            canScheduleExactAlarms = true,
            isIgnoringBatteryOptimizations = true,
            canWriteSettings = true,
            forceSimDialEnabled = true,
            holdsCallRedirectionRole = false,
            callRedirectionRoleAvailable = true,
        )
        assertFalse(status.needsCallRedirectionRole)
        assertFalse(status.needsAttention)
    }
}
