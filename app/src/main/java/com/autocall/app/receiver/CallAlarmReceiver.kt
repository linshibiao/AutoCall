package com.autocall.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autocall.app.service.CallTriggerService
import com.autocall.app.util.PermissionHelper

class CallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduledCallId = intent.getLongExtra(EXTRA_SCHEDULED_CALL_ID, -1L)
        if (scheduledCallId < 0) return

        val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, -1)
        if (dayOfWeek < 0) return

        if (!PermissionHelper.hasCallPhonePermission(context)) return

        val retryAttempt = intent.getIntExtra(EXTRA_RETRY_ATTEMPT, 0)
        CallTriggerService.start(context, scheduledCallId, dayOfWeek, retryAttempt)
    }

    companion object {
        const val ACTION_TRIGGER_CALL = "com.autocall.app.ACTION_TRIGGER_CALL"
        const val EXTRA_SCHEDULED_CALL_ID = "extra_scheduled_call_id"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_RETRY_ATTEMPT = "extra_retry_attempt"
    }
}
