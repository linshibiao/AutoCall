package com.autocall.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.autocall.app.data.ScheduledCall
import com.autocall.app.data.ScheduledCallDao
import com.autocall.app.receiver.CallAlarmReceiver
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleCall(scheduledCall: ScheduledCall) {
        if (!scheduledCall.isEnabled) return

        val triggerAtMillis = nextTriggerMillis(
            dayOfWeek = scheduledCall.dayOfWeek,
            hour = scheduledCall.hour,
            minute = scheduledCall.minute,
        )

        val pendingIntent = pendingIntentFor(scheduledCall.id)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
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

        /**
         * Returns the next wall-clock time (in millis) for the given weekly schedule.
         * If today's slot has not passed yet, returns today; otherwise rolls forward to
         * the same weekday next week.
         */
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
    }
}
