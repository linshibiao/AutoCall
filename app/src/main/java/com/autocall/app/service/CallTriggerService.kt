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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autocall.app.MainActivity
import com.autocall.app.R
import com.autocall.app.call.BusyDeferLogic
import com.autocall.app.call.BusyDeferNotifier
import com.autocall.app.call.BusyDeferState
import com.autocall.app.call.BusyDeferStore
import com.autocall.app.call.CallLauncher
import com.autocall.app.call.CallRetryCoordinator
import com.autocall.app.call.SpeakerphoneHelper
import com.autocall.app.data.AppDatabase
import com.autocall.app.data.AppSettings
import com.autocall.app.data.ScheduledCall
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
        val deferredSinceMillis = intent?.getLongExtra(
            CallAlarmReceiver.EXTRA_DEFERRED_SINCE_MILLIS,
            -1L,
        ) ?: -1L
        val forcePlace = intent?.getBooleanExtra(EXTRA_FORCE_PLACE, false) ?: false

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
                handleScheduledCall(
                    scheduledCallId = scheduledCallId,
                    dayOfWeek = dayOfWeek,
                    retryAttempt = retryAttempt,
                    deferredSinceMillis = deferredSinceMillis,
                    forcePlace = forcePlace,
                )
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
        deferredSinceMillis: Long,
        forcePlace: Boolean,
    ) {
        val dao = AppDatabase.getInstance(this).scheduledCallDao()
        val scheduledCall = dao.getById(scheduledCallId)
        if (scheduledCall == null || !scheduledCall.isEnabled) {
            clearBusyDefer(scheduledCallId)
            CallRetryCoordinator.abort(this)
            return
        }
        if (!scheduledCall.days().contains(dayOfWeek)) {
            if (retryAttempt > 0) {
                CallRetryCoordinator.abort(this)
            }
            clearBusyDefer(scheduledCallId)
            return
        }

        val canTrackDuration = PermissionHelper.hasReadPhoneStatePermission(this)
        val shouldRetry = scheduledCall.hasExpectedDuration() && canTrackDuration

        if (retryAttempt > 0 && !shouldRetry) {
            clearBusyDefer(scheduledCallId)
            CallRetryCoordinator.abort(this)
            return
        }

        if (retryAttempt > 0 && CallRetryCoordinator.giveUpIfPastDeadline(this)) {
            clearBusyDefer(scheduledCallId)
            return
        }

        // Never placeCall while another call is active: Android holds/drops the prior call,
        // CallWatchStore is single-slot, and IDLE never fires until every call ends — so
        // duration and redial for both calls break. Defer until the line and watch slot are free.
        if (
            CallRetryCoordinator.shouldDeferDial(
                context = this,
                scheduledCallId = scheduledCallId,
                retryAttempt = retryAttempt,
                forcePlace = forcePlace,
            )
        ) {
            handleBusyDefer(
                scheduledCall = scheduledCall,
                dayOfWeek = dayOfWeek,
                retryAttempt = retryAttempt,
                deferredSinceMillis = deferredSinceMillis,
                // Place was tapped but another watch session still owns the slot — keep polling.
                resumePolling = forcePlace,
            )
            return
        }

        clearBusyDefer(scheduledCallId)

        if (canTrackDuration && retryAttempt == 0) {
            if (!CallRetryCoordinator.beginSession(this, scheduledCall, dayOfWeek)) {
                handleBusyDefer(
                    scheduledCall = scheduledCall,
                    dayOfWeek = dayOfWeek,
                    retryAttempt = retryAttempt,
                    deferredSinceMillis = deferredSinceMillis,
                    resumePolling = forcePlace,
                )
                return
            }
        }

        val placed = CallLauncher.placeCall(
            this,
            scheduledCall.phoneNumber,
            scheduledCall.useSpeakerphone,
        )
        if (!placed) {
            if (canTrackDuration && PermissionHelper.hasCallPhonePermission(this)) {
                CallRetryCoordinator.onPlacementFailed(this)
            } else if (canTrackDuration) {
                CallRetryCoordinator.abort(this)
            }
            return
        }

        if (canTrackDuration) {
            CallRetryCoordinator.onCallPlaced(this)
        }

        if (retryAttempt == 0) {
            AlarmScheduler(this).scheduleCallDay(scheduledCall, dayOfWeek)
        }

        if (scheduledCall.useSpeakerphone) {
            enableSpeakerphoneWithRetries()
        }
    }

    private fun handleBusyDefer(
        scheduledCall: ScheduledCall,
        dayOfWeek: Int,
        retryAttempt: Int,
        deferredSinceMillis: Long,
        resumePolling: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val existing = BusyDeferStore.load(this, scheduledCall.id)
        val promptMinutes = AppSettings(this).getRetrySettings().busyDeferPromptMinutes
        val plan = BusyDeferLogic.planBusyDefer(
            nowMillis = now,
            deferredSinceMillisFromIntent = deferredSinceMillis,
            existing = existing,
            scheduledCallId = scheduledCall.id,
            promptMinutes = promptMinutes,
            resumePolling = resumePolling,
        )

        if (plan.phase == BusyDeferLogic.DeferPhase.AWAITING_USER) {
            Log.i(TAG, "Call ${scheduledCall.id} still awaiting user decision after busy delay")
            return
        }

        val state = BusyDeferState(
            scheduledCallId = scheduledCall.id,
            dayOfWeek = dayOfWeek,
            retryAttempt = retryAttempt,
            deferredSinceMillis = plan.deferredSinceMillis,
            contactName = scheduledCall.contactName,
            phoneNumber = scheduledCall.phoneNumber,
            awaitingUserDecision = plan.awaitingUserDecision,
        )

        when (plan.phase) {
            BusyDeferLogic.DeferPhase.FIRST_DELAY_AND_POLL -> {
                Log.i(TAG, "Deferring call ${scheduledCall.id}: notifying user immediately")
                BusyDeferStore.save(this, state)
                BusyDeferNotifier.showDelayed(this, state)
            }

            BusyDeferLogic.DeferPhase.SHOW_DECISION_PROMPT -> {
                Log.i(
                    TAG,
                    "Call ${scheduledCall.id} delayed over $promptMinutes min; asking user",
                )
                BusyDeferStore.save(this, state)
                BusyDeferNotifier.showDecisionPrompt(this, state, promptMinutes)
                if (retryAttempt == 0) {
                    AlarmScheduler(this).scheduleCallDay(scheduledCall, dayOfWeek)
                }
                return
            }

            BusyDeferLogic.DeferPhase.CONTINUE_POLL -> {
                BusyDeferStore.save(this, state)
                if (resumePolling) {
                    BusyDeferNotifier.showDelayed(this, state)
                }
            }

            BusyDeferLogic.DeferPhase.AWAITING_USER -> return
        }

        if (!plan.scheduleBusyPoll) return

        Log.i(
            TAG,
            "Deferring call ${scheduledCall.id} (attempt $retryAttempt): line or watch session busy",
        )
        val deferred = AlarmScheduler(this).scheduleRetry(
            scheduledCallId = scheduledCall.id,
            dayOfWeek = dayOfWeek,
            retryAttempt = retryAttempt,
            delayMs = CallRetryCoordinator.BUSY_DEFER_DELAY_MS,
            deferredSinceMillis = plan.deferredSinceMillis,
        )
        if (retryAttempt == 0) {
            AlarmScheduler(this).scheduleCallDay(scheduledCall, dayOfWeek)
        }
        if (!deferred) {
            Log.w(TAG, "Failed to schedule busy poll for call ${scheduledCall.id}; prompting user")
            val awaiting = state.copy(awaitingUserDecision = true)
            BusyDeferStore.save(this, awaiting)
            BusyDeferNotifier.showDecisionPrompt(this, awaiting, promptMinutes)
        }
    }

    private fun clearBusyDefer(scheduledCallId: Long) {
        BusyDeferStore.clear(this, scheduledCallId)
        BusyDeferNotifier.cancel(this, scheduledCallId)
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
        private const val TAG = "CallTriggerService"
        const val CHANNEL_ID = "autocall_trigger"
        const val EXTRA_FORCE_PLACE = "extra_force_place"
        private const val NOTIFICATION_ID = 1001
        private const val SPEAKERPHONE_RETRY_COUNT = 5
        private const val SPEAKERPHONE_RETRY_DELAY_MS = 1_500L

        fun start(
            context: Context,
            scheduledCallId: Long,
            dayOfWeek: Int,
            retryAttempt: Int = 0,
            deferredSinceMillis: Long = -1L,
            forcePlace: Boolean = false,
        ) {
            val intent = Intent(context, CallTriggerService::class.java).apply {
                putExtra(CallAlarmReceiver.EXTRA_SCHEDULED_CALL_ID, scheduledCallId)
                putExtra(CallAlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
                putExtra(CallAlarmReceiver.EXTRA_RETRY_ATTEMPT, retryAttempt)
                putExtra(CallAlarmReceiver.EXTRA_DEFERRED_SINCE_MILLIS, deferredSinceMillis)
                putExtra(EXTRA_FORCE_PLACE, forcePlace)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
