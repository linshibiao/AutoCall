package com.autocall.app.call

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autocall.app.MainActivity
import com.autocall.app.R
import com.autocall.app.util.DayOfWeekFormatter
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FailureNotifier {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_alert_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, session: CallWatchSession) {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; failure alert was not shown")
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contact = session.contactName?.takeIf { it.isNotBlank() }
        val titleName = contact ?: session.phoneNumber.ifBlank { "unknown contact" }
        val scheduledTime = formatScheduleTime(session.originalScheduleMillis)
        val retryCount = session.retryAttempt
        val summary = appContext.getString(R.string.notification_retry_failed_summary, scheduledTime)
        val details = when {
            contact != null && retryCount > 0 -> appContext.getString(
                R.string.notification_retry_failed_text,
                contact,
                session.phoneNumber,
                scheduledTime,
                retryCount,
            )
            contact != null -> appContext.getString(
                R.string.notification_retry_failed_text_no_retry,
                contact,
                session.phoneNumber,
                scheduledTime,
            )
            retryCount > 0 -> appContext.getString(
                R.string.notification_retry_failed_text_unknown,
                session.phoneNumber,
                scheduledTime,
                retryCount,
            )
            else -> appContext.getString(
                R.string.notification_retry_failed_text_unknown_no_retry,
                session.phoneNumber,
                scheduledTime,
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.notification_retry_failed_title, titleName))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationId = NOTIFICATION_ID_BASE + session.scheduledCallId.toInt()
        try {
            manager.notify(notificationId, notification)
            Log.w(TAG, "Posted failure notification for call ${session.scheduledCallId}")
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Missing notification permission; failure alert was not shown", securityException)
        }
    }

    private fun formatScheduleTime(scheduleMillis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = scheduleMillis }
        val day = calendar.getDisplayName(
            Calendar.DAY_OF_WEEK,
            Calendar.LONG,
            Locale.getDefault(),
        ) ?: DayOfWeekFormatter.label(calendar.get(Calendar.DAY_OF_WEEK))
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(scheduleMillis))
        val time = DayOfWeekFormatter.formatTime(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
        return "$day, $date at $time"
    }

    const val CHANNEL_ID = "autocall_alerts"
    private const val TAG = "FailureNotifier"
    private const val NOTIFICATION_ID_BASE = 2001
    private const val REQUEST_CODE = 2101
}
