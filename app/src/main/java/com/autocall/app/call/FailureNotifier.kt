package com.autocall.app.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.autocall.app.MainActivity
import com.autocall.app.R

object FailureNotifier {

    fun show(context: Context, session: CallWatchSession) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannel(context, manager)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val target = session.contactName?.takeIf { it.isNotBlank() } ?: session.phoneNumber
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notification_retry_failed_title))
            .setContentText(
                context.getString(R.string.notification_retry_failed_text, target),
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.notification_retry_failed_text, target),
                ),
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_alert_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    const val CHANNEL_ID = "autocall_alerts"
    private const val NOTIFICATION_ID = 2001
}
