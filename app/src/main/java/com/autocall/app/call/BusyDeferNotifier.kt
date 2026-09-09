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
import com.autocall.app.receiver.BusyDeferActionReceiver

object BusyDeferNotifier {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_busy_defer_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_busy_defer_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun showDelayed(context: Context, state: BusyDeferState) {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        val titleName = displayName(state)
        val notification = baseBuilder(appContext, state)
            .setContentTitle(
                appContext.getString(R.string.notification_busy_defer_delayed_title, titleName),
            )
            .setContentText(appContext.getString(R.string.notification_busy_defer_delayed_text))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    appContext.getString(
                        R.string.notification_busy_defer_delayed_details,
                        titleName,
                        state.phoneNumber,
                    ),
                ),
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

        notify(appContext, state.scheduledCallId, notification)
    }

    @SuppressLint("MissingPermission")
    fun showDecisionPrompt(context: Context, state: BusyDeferState, promptMinutes: Int) {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        val titleName = displayName(state)
        val placeIntent = actionPendingIntent(
            appContext,
            state,
            BusyDeferActionReceiver.ACTION_PLACE,
            REQUEST_PLACE,
        )
        val skipIntent = actionPendingIntent(
            appContext,
            state,
            BusyDeferActionReceiver.ACTION_SKIP,
            REQUEST_SKIP,
        )

        val notification = baseBuilder(appContext, state)
            .setContentTitle(
                appContext.getString(R.string.notification_busy_defer_prompt_title, titleName),
            )
            .setContentText(
                appContext.getString(R.string.notification_busy_defer_prompt_text, promptMinutes),
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    appContext.getString(
                        R.string.notification_busy_defer_prompt_details,
                        titleName,
                        state.phoneNumber,
                        promptMinutes,
                    ),
                ),
            )
            .setOnlyAlertOnce(false)
            .setOngoing(true)
            .addAction(
                0,
                appContext.getString(R.string.notification_busy_defer_action_place),
                placeIntent,
            )
            .addAction(
                0,
                appContext.getString(R.string.notification_busy_defer_action_skip),
                skipIntent,
            )
            .build()

        notify(appContext, state.scheduledCallId, notification)
    }

    fun cancel(context: Context, scheduledCallId: Long) {
        NotificationManagerCompat.from(context.applicationContext)
            .cancel(notificationId(scheduledCallId))
    }

    private fun baseBuilder(
        context: Context,
        state: BusyDeferState,
    ): NotificationCompat.Builder {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CONTENT + state.scheduledCallId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
    }

    private fun actionPendingIntent(
        context: Context,
        state: BusyDeferState,
        action: String,
        requestCodeBase: Int,
    ): PendingIntent {
        val intent = Intent(context, BusyDeferActionReceiver::class.java).apply {
            this.action = action
            putExtra(BusyDeferActionReceiver.EXTRA_SCHEDULED_CALL_ID, state.scheduledCallId)
            putExtra(BusyDeferActionReceiver.EXTRA_DAY_OF_WEEK, state.dayOfWeek)
            putExtra(BusyDeferActionReceiver.EXTRA_RETRY_ATTEMPT, state.retryAttempt)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeBase + state.scheduledCallId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, scheduledCallId: Long, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; busy-defer alert was not shown")
        }
        try {
            manager.notify(notificationId(scheduledCallId), notification)
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Missing notification permission; busy-defer alert was not shown", securityException)
        }
    }

    private fun displayName(state: BusyDeferState): String =
        BusyDeferLogic.displayName(state.contactName, state.phoneNumber)

    private fun notificationId(scheduledCallId: Long): Int =
        NOTIFICATION_ID_BASE + scheduledCallId.toInt()

    const val CHANNEL_ID = "autocall_busy_defer"
    private const val TAG = "BusyDeferNotifier"
    private const val NOTIFICATION_ID_BASE = 3001
    private const val REQUEST_CONTENT = 3101
    private const val REQUEST_PLACE = 3201
    private const val REQUEST_SKIP = 3301
}
