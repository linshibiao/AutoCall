package com.autocall.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.autocall.app.MainActivity
import com.autocall.app.data.ScheduledCall
import com.autocall.app.data.ScheduledCallDao
import com.autocall.app.receiver.CallAlarmReceiver
import com.autocall.app.util.PermissionHelper
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleCall(scheduledCall: ScheduledCall): Boolean {
        if (!scheduledCall.isEnabled) return false

        var scheduledAny = false
        scheduledCall.days().forEach { dayOfWeek ->
            if (scheduleCallDay(scheduledCall, dayOfWeek)) {
                scheduledAny = true
            }
        }
        return scheduledAny
    }

    fun scheduleCallDay(scheduledCall: ScheduledCall, dayOfWeek: Int): Boolean {
        if (!scheduledCall.isEnabled) return false

        if (!PermissionHelper.canScheduleExactAlarms(context)) {
            Log.w(TAG, "Exact alarm permission missing; cannot schedule call ${scheduledCall.id}")
            return false
        }

        val triggerAtMillis = nextTriggerMillis(
            dayOfWeek = dayOfWeek,
            hour = scheduledCall.hour,
            minute = scheduledCall.minute,
        )

        val operationIntent = pendingIntentFor(scheduledCall.id, dayOfWeek)
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(scheduledCall.id, dayOfWeek),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, operationIntent)
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operationIntent,
                )
            }
            Log.d(
                TAG,
                "Scheduled call ${scheduledCall.id} on day $dayOfWeek for ${formatTriggerTime(triggerAtMillis)}",
            )
            true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Failed to schedule call ${scheduledCall.id} on day $dayOfWeek", securityException)
            false
        }
    }

    fun cancelCall(scheduledCall: ScheduledCall) {
        scheduledCall.days().forEach { dayOfWeek ->
            cancelCallDay(scheduledCall.id, dayOfWeek)
        }
        cancelRetry(scheduledCall.id)
    }

    fun cancelCall(id: Long) {
        for (dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY) {
            cancelCallDay(id, dayOfWeek)
        }
        cancelRetry(id)
    }

    fun scheduleRetry(
        scheduledCallId: Long,
        dayOfWeek: Int,
        retryAttempt: Int,
        delayMs: Long,
    ): Boolean {
        if (!PermissionHelper.canScheduleExactAlarms(context)) {
            Log.w(TAG, "Exact alarm permission missing; cannot retry call $scheduledCallId")
            return false
        }

        val triggerAtMillis = System.currentTimeMillis() + delayMs
        val operationIntent = retryPendingIntent(scheduledCallId, dayOfWeek, retryAttempt)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operationIntent,
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operationIntent,
                )
            }
            Log.d(TAG, "Scheduled retry $retryAttempt for call $scheduledCallId")
            true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Failed to schedule retry for call $scheduledCallId", securityException)
            false
        }
    }

    fun cancelRetry(scheduledCallId: Long) {
        for (dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY) {
            alarmManager.cancel(retryPendingIntent(scheduledCallId, dayOfWeek, 0))
        }
    }

    private fun cancelCallDay(id: Long, dayOfWeek: Int) {
        alarmManager.cancel(pendingIntentFor(id, dayOfWeek))
    }

    suspend fun rescheduleAll(dao: ScheduledCallDao) {
        dao.getAllEnabled().forEach { scheduleCall(it) }
    }

    private fun pendingIntentFor(scheduledCallId: Long, dayOfWeek: Int): PendingIntent {
        val intent = Intent(context, CallAlarmReceiver::class.java).apply {
            action = CallAlarmReceiver.ACTION_TRIGGER_CALL
            putExtra(CallAlarmReceiver.EXTRA_SCHEDULED_CALL_ID, scheduledCallId)
            putExtra(CallAlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            requestCode(scheduledCallId, dayOfWeek),
            intent,
            flags,
        )
    }

    private fun retryPendingIntent(
        scheduledCallId: Long,
        dayOfWeek: Int,
        retryAttempt: Int,
    ): PendingIntent {
        val intent = Intent(context, CallAlarmReceiver::class.java).apply {
            action = CallAlarmReceiver.ACTION_TRIGGER_CALL
            putExtra(CallAlarmReceiver.EXTRA_SCHEDULED_CALL_ID, scheduledCallId)
            putExtra(CallAlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
            putExtra(CallAlarmReceiver.EXTRA_RETRY_ATTEMPT, retryAttempt)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            retryRequestCode(scheduledCallId, dayOfWeek),
            intent,
            flags,
        )
    }

    companion object {
        private const val TAG = "AlarmScheduler"

        fun requestCode(scheduledCallId: Long, dayOfWeek: Int): Int =
            (scheduledCallId.toInt() * 10) + dayOfWeek

        fun retryRequestCode(scheduledCallId: Long, dayOfWeek: Int): Int =
            10_000 + (scheduledCallId.toInt() * 10) + dayOfWeek

        fun nextTriggerMillis(dayOfWeek: Int, hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val currentDay = now.get(Calendar.DAY_OF_WEEK)
            var daysUntil = dayOfWeek - currentDay
            if (daysUntil < 0) {
                daysUntil += 7
            } else if (daysUntil == 0 && target.timeInMillis <= now.timeInMillis) {
                daysUntil = 7
            }

            target.add(Calendar.DAY_OF_YEAR, daysUntil)
            return target.timeInMillis
        }

        fun nextTriggerMillis(days: Set<Int>, hour: Int, minute: Int): Long? {
            if (days.isEmpty()) return null
            return days.minOf { nextTriggerMillis(it, hour, minute) }
        }

        fun formatTriggerTime(triggerAtMillis: Long): String {
            val calendar = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
            val day = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault())
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            return "$day ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        }
    }
}
