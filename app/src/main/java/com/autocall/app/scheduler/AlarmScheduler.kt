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

        if (!PermissionHelper.canScheduleExactAlarms(context)) {
            Log.w(TAG, "Exact alarm permission missing; cannot schedule call ${scheduledCall.id}")
            return false
        }

        val triggerAtMillis = nextTriggerMillis(
            dayOfWeek = scheduledCall.dayOfWeek,
            hour = scheduledCall.hour,
            minute = scheduledCall.minute,
        )

        val operationIntent = pendingIntentFor(scheduledCall.id)
        val showIntent = PendingIntent.getActivity(
            context,
            scheduledCall.id.toInt(),
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
                "Scheduled call ${scheduledCall.id} for ${formatTriggerTime(triggerAtMillis)}",
            )
            true
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Failed to schedule call ${scheduledCall.id}", securityException)
            false
        }
    }

    fun cancelCall(id: Long) {
        alarmManager.cancel(pendingIntentFor(id))
    }

    suspend fun rescheduleAll(dao: ScheduledCallDao) {
        dao.getAllEnabled().forEach { scheduleCall(it) }
    }

    private fun pendingIntentFor(scheduledCallId: Long): PendingIntent {
        val intent = Intent(context, CallAlarmReceiver::class.java).apply {
            action = CallAlarmReceiver.ACTION_TRIGGER_CALL
            putExtra(CallAlarmReceiver.EXTRA_SCHEDULED_CALL_ID, scheduledCallId)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            scheduledCallId.toInt(),
            intent,
            flags,
        )
    }

    companion object {
        private const val TAG = "AlarmScheduler"

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

        fun formatTriggerTime(triggerAtMillis: Long): String {
            val calendar = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
            val day = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, java.util.Locale.getDefault())
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            return "$day ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        }
    }
}
