package com.autocall.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autocall.app.MainActivity
import com.autocall.app.R
import com.autocall.app.call.CallLauncher
import com.autocall.app.call.CallRetryCoordinator
import com.autocall.app.call.SpeakerphoneHelper
import com.autocall.app.data.AppDatabase
import com.autocall.app.receiver.CallAlarmReceiver
import com.autocall.app.scheduler.AlarmScheduler
import com.autocall.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallTriggerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scheduledCallId = intent?.getLongExtra(
            CallAlarmReceiver.EXTRA_SCHEDULED_CALL_ID,
            -1L,
        ) ?: -1L

        val dayOfWeek = intent?.getIntExtra(
            CallAlarmReceiver.EXTRA_DAY_OF_WEEK,
            -1,
        ) ?: -1

        val retryAttempt = intent?.getIntExtra(CallAlarmReceiver.EXTRA_RETRY_ATTEMPT, 0) ?: 0

        if (scheduledCallId < 0 || dayOfWeek < 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                handleScheduledCall(scheduledCallId, dayOfWeek, retryAttempt)
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private suspend fun handleScheduledCall(
        scheduledCallId: Long,
        dayOfWeek: Int,
        retryAttempt: Int,
    ) {
        val dao = AppDatabase.getInstance(this).scheduledCallDao()
        val scheduledCall = dao.getById(scheduledCallId)
        if (scheduledCall == null || !scheduledCall.isEnabled) {
            CallRetryCoordinator.abort(this)
            return
        }
        if (!scheduledCall.days().contains(dayOfWeek)) {
            if (retryAttempt > 0) {
                CallRetryCoordinator.abort(this)
            }
            return
        }

        val shouldWatch = scheduledCall.hasExpectedDuration() &&
            PermissionHelper.hasReadPhoneStatePermission(this)

        if (retryAttempt > 0 && !shouldWatch) {
            CallRetryCoordinator.abort(this)
            return
        }

        if (retryAttempt > 0 && CallRetryCoordinator.giveUpIfPastDeadline(this)) {
            return
        }

        if (shouldWatch && retryAttempt == 0) {
            CallRetryCoordinator.beginSession(this, scheduledCall, dayOfWeek)
        }

        val placed = CallLauncher.placeCall(
            this,
            scheduledCall.phoneNumber,
            scheduledCall.useSpeakerphone,
        )
        if (!placed) {
            if (shouldWatch && PermissionHelper.hasCallPhonePermission(this)) {
                CallRetryCoordinator.onPlacementFailed(this)
            } else if (shouldWatch) {
                CallRetryCoordinator.abort(this)
            }
            return
        }

        if (shouldWatch) {
            CallRetryCoordinator.onCallPlaced(this)
        }

        if (retryAttempt == 0) {
            AlarmScheduler(this).scheduleCallDay(scheduledCall, dayOfWeek)
        }

        if (scheduledCall.useSpeakerphone) {
            enableSpeakerphoneWithRetries()
        }
    }

    private suspend fun enableSpeakerphoneWithRetries() {
        SpeakerphoneHelper.enable(this)
        repeat(SPEAKERPHONE_RETRY_COUNT) {
            delay(SPEAKERPHONE_RETRY_DELAY_MS)
            SpeakerphoneHelper.enable(this)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(getString(R.string.notification_call_title))
            .setContentText(getString(R.string.notification_call_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "autocall_trigger"
        private const val NOTIFICATION_ID = 1001
        private const val SPEAKERPHONE_RETRY_COUNT = 5
        private const val SPEAKERPHONE_RETRY_DELAY_MS = 1_500L

        fun start(
            context: Context,
            scheduledCallId: Long,
            dayOfWeek: Int,
            retryAttempt: Int = 0,
        ) {
            val intent = Intent(context, CallTriggerService::class.java).apply {
                putExtra(CallAlarmReceiver.EXTRA_SCHEDULED_CALL_ID, scheduledCallId)
                putExtra(CallAlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
                putExtra(CallAlarmReceiver.EXTRA_RETRY_ATTEMPT, retryAttempt)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
