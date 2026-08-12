package com.autocall.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autocall.app.MainActivity
import com.autocall.app.R
import com.autocall.app.call.CallLauncher
import com.autocall.app.data.AppDatabase
import com.autocall.app.receiver.CallAlarmReceiver
import com.autocall.app.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
                handleScheduledCall(scheduledCallId, dayOfWeek)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleScheduledCall(scheduledCallId: Long, dayOfWeek: Int) {
        val dao = AppDatabase.getInstance(this).scheduledCallDao()
        val scheduledCall = dao.getById(scheduledCallId) ?: return
        if (!scheduledCall.isEnabled) return
        if (!scheduledCall.days().contains(dayOfWeek)) return

        val placed = CallLauncher.placeCall(this, scheduledCall.phoneNumber)
        if (!placed) return

        if (scheduledCall.useSpeakerphone) {
            enableSpeakerphoneAfterDelay()
        }

        AlarmScheduler(this).scheduleCallDay(scheduledCall, dayOfWeek)
    }

    private fun enableSpeakerphoneAfterDelay() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Handler(Looper.getMainLooper()).postDelayed({
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }, SPEAKERPHONE_DELAY_MS)
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
        private const val SPEAKERPHONE_DELAY_MS = 2_000L

        fun start(context: Context, scheduledCallId: Long, dayOfWeek: Int) {
            val intent = Intent(context, CallTriggerService::class.java).apply {
                putExtra(CallAlarmReceiver.EXTRA_SCHEDULED_CALL_ID, scheduledCallId)
                putExtra(CallAlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
