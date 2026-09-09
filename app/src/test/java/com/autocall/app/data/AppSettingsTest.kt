package com.autocall.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppSettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("autocall_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun getRetrySettings_returnsDefaults_whenUnset() {
        val settings = AppSettings(context).getRetrySettings()
        assertEquals(RetrySettings.DEFAULT_MAX_RETRIES, settings.maxRetries)
        assertEquals(RetrySettings.DEFAULT_RETRY_DEADLINE_MINUTES, settings.retryDeadlineMinutes)
        assertEquals(
            RetrySettings.DEFAULT_BUSY_DEFER_PROMPT_MINUTES,
            settings.busyDeferPromptMinutes,
        )
    }

    @Test
    fun setRetrySettings_persistsBusyDeferPrompt_andCoerces() {
        val settings = AppSettings(context)
        val saved = settings.setRetrySettings(
            maxRetries = 4,
            retryDeadlineMinutes = 15,
            busyDeferPromptMinutes = 0,
        )
        assertEquals(1, saved.busyDeferPromptMinutes)
        assertEquals(1, settings.getRetrySettings().busyDeferPromptMinutes)
        assertEquals(4, settings.getRetrySettings().maxRetries)
        assertEquals(15, settings.getRetrySettings().retryDeadlineMinutes)
    }
}
