package com.autocall.app.util

import android.Manifest
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHelperEndCallTest {

    @Test
    fun requiredRuntimePermissions_includesAnswerPhoneCalls() {
        assertTrue(
            PermissionHelper.requiredRuntimePermissions.contains(
                Manifest.permission.ANSWER_PHONE_CALLS,
            ),
        )
    }

    @Test
    fun allRuntimePermissions_includesAnswerPhoneCalls() {
        assertTrue(
            PermissionHelper.allRuntimePermissions().contains(
                Manifest.permission.ANSWER_PHONE_CALLS,
            ),
        )
    }
}
