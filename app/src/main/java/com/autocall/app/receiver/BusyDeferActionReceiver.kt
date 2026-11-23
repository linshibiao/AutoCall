package com.autocall.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autocall.app.call.BusyDeferNotifier
import com.autocall.app.call.BusyDeferStore
import com.autocall.app.call.CallRetryCoordinator
import com.autocall.app.scheduler.AlarmScheduler
import com.autocall.app.service.CallTriggerService

class BusyDeferActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduledCallId = intent.getLongExtra(EXTRA_SCHEDULED_CALL_ID, -1L)
        if (scheduledCallId < 0) return

        val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, -1)
        val retryAttempt = intent.getIntExtra(EXTRA_RETRY_ATTEMPT, 0)
        val isTestCall = intent.getBooleanExtra(EXTRA_TEST_CALL, false)
        val appContext = context.applicationContext

        when (intent.action) {
            ACTION_PLACE -> {
                Log.i(TAG, "User chose to place deferred call $scheduledCallId")
                // Read store before clear so a stale Place PI without EXTRA_TEST_CALL
                // still preserves test-call treatment.
                val storedIsTestCall =
                    BusyDeferStore.load(appContext, scheduledCallId)?.isTestCall == true
                val treatAsTest = isTestCall || storedIsTestCall
                BusyDeferStore.clear(appContext, scheduledCallId)
                BusyDeferNotifier.cancel(appContext, scheduledCallId)
                AlarmScheduler(appContext).cancelRetry(scheduledCallId)
                CallTriggerService.start(
                    context = appContext,
                    scheduledCallId = scheduledCallId,
                    dayOfWeek = dayOfWeek,
                    retryAttempt = retryAttempt,
                    forcePlace = true,
                    isTestCall = treatAsTest,
                )
            }

            ACTION_SKIP -> {
                Log.i(TAG, "User chose to skip deferred call $scheduledCallId")
                BusyDeferStore.clear(appContext, scheduledCallId)
                BusyDeferNotifier.cancel(appContext, scheduledCallId)
                AlarmScheduler(appContext).cancelRetry(scheduledCallId)
                // Clear a same-call orphan/stale watch session; never abort a different call.
                CallRetryCoordinator.abortIfSessionFor(appContext, scheduledCallId)
            }
        }
    }

    companion object {
        const val ACTION_PLACE = "com.autocall.app.ACTION_BUSY_DEFER_PLACE"
        const val ACTION_SKIP = "com.autocall.app.ACTION_BUSY_DEFER_SKIP"
        const val EXTRA_SCHEDULED_CALL_ID = "extra_scheduled_call_id"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        const val EXTRA_RETRY_ATTEMPT = "extra_retry_attempt"
        const val EXTRA_TEST_CALL = "extra_test_call"
        private const val TAG = "BusyDeferActionReceiver"
    }
}
