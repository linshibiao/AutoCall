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

        if (!PermissionHelper.hasCallPhonePermission(context)) return

        CallTriggerService.start(context, scheduledCallId)
    }

    companion object {
        const val ACTION_TRIGGER_CALL = "com.autocall.app.ACTION_TRIGGER_CALL"
        const val EXTRA_SCHEDULED_CALL_ID = "extra_scheduled_call_id"
    }
}
