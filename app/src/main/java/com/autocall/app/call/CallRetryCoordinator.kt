package com.autocall.app.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import com.autocall.app.data.AppDatabase
import com.autocall.app.data.AppSettings
import com.autocall.app.data.CallDurationLog
import com.autocall.app.data.RetrySettings
import com.autocall.app.data.ScheduledCall
import com.autocall.app.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

object CallRetryCoordinator {

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitor: CallStateMonitor? = null
    private var speakerphoneJob: Job? = null
    private var watchTimeoutJob: Job? = null

    fun restore(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (CallWatchStore.load(appContext) != null) {
                ensureMonitorLocked(appContext)
            }
        }
    }

    fun beginSession(context: Context, scheduledCall: ScheduledCall, dayOfWeek: Int) {
        val expected = scheduledCall.expectedDurationSeconds?.takeIf { it > 0 } ?: 0

        val appContext = context.applicationContext
        synchronized(lock) {
            CallWatchStore.load(appContext)?.let { existing ->
                AlarmScheduler(appContext).cancelRetry(existing.scheduledCallId)
            }
            AlarmScheduler(appContext).cancelRetry(scheduledCall.id)
            CallWatchStore.save(
                appContext,
                CallWatchSession(
                    scheduledCallId = scheduledCall.id,
                    dayOfWeek = dayOfWeek,
                    retryAttempt = 0,
                    expectedDurationSeconds = expected,
                    phase = CallWatchPhase.RETRY_PENDING,
                    originalScheduleMillis = originalScheduleMillis(
                        dayOfWeek = dayOfWeek,
                        hour = scheduledCall.hour,
                        minute = scheduledCall.minute,
                    ),
                    placedAtMillis = System.currentTimeMillis(),
                    offHookAtMillis = null,
                    contactName = scheduledCall.contactName,
                    phoneNumber = scheduledCall.phoneNumber,
                    useSpeakerphone = scheduledCall.useSpeakerphone,
                    successWindowSeconds = scheduledCall.successWindowSeconds.coerceIn(
                        ScheduledCall.MIN_SUCCESS_WINDOW_SECONDS,
                        ScheduledCall.MAX_SUCCESS_WINDOW_SECONDS,
                    ),
                ),
            )
            ensureMonitorLocked(appContext)
        }
    }

    fun onCallPlaced(context: Context) {
        val appContext = context.applicationContext
        val timeoutSeconds: Int
        val sessionId: Long
        val placedAt: Long
        synchronized(lock) {
            val session = CallWatchStore.load(appContext) ?: return
            val placedAtMillis = System.currentTimeMillis()
            CallWatchStore.save(
                appContext,
                session.copy(
                    phase = CallWatchPhase.WAITING_OFFHOOK,
                    placedAtMillis = placedAtMillis,
                    offHookAtMillis = null,
                ),
            )
            ensureMonitorLocked(appContext)
            timeoutSeconds = if (session.expectedDurationSeconds > 0) {
                session.expectedDurationSeconds + session.successWindowSeconds + 15
            } else {
                DEFAULT_DURATION_ONLY_TIMEOUT_SECONDS
            }
            sessionId = session.scheduledCallId
            placedAt = placedAtMillis
        }
        startWatchTimeout(appContext, sessionId, placedAt, timeoutSeconds)
    }

    fun onPlacementFailed(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val session = CallWatchStore.load(appContext) ?: return
            if (session.phase == CallWatchPhase.RETRY_PENDING) {
                CallWatchStore.save(
                    appContext,
                    session.copy(phase = CallWatchPhase.WAITING_OFFHOOK),
                )
            }
        }
        evaluateEndedCall(appContext, actualDurationSeconds = 0)
    }

    fun abort(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val session = CallWatchStore.load(appContext)
            if (session != null) {
                AlarmScheduler(appContext).cancelRetry(session.scheduledCallId)
            }
            stopSpeakerphoneLocked()
            stopWatchTimeoutLocked()
            stopMonitorLocked()
            CallWatchStore.clear(appContext)
            if (session != null && session.retryAttempt > 0) {
                FailureNotifier.show(appContext, session)
                Log.w(TAG, "Aborted call ${session.scheduledCallId} after retries; notified user")
            }
        }
    }

    fun giveUpIfPastDeadline(context: Context): Boolean {
        val appContext = context.applicationContext
        synchronized(lock) {
            val session = CallWatchStore.load(appContext) ?: return false
            val settings = AppSettings(appContext).getRetrySettings()
            if (!isPastRetryDeadline(session, settings)) return false
            giveUpLocked(appContext, session, "past retry deadline")
            return true
        }
    }

    fun onPhoneState(context: Context, state: Int) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val durationToEvaluate: Int? = synchronized(lock) {
            val session = CallWatchStore.load(appContext) ?: return
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (session.phase == CallWatchPhase.WAITING_OFFHOOK) {
                        CallWatchStore.save(
                            appContext,
                            session.copy(
                                phase = CallWatchPhase.IN_CALL,
                                offHookAtMillis = now,
                            ),
                        )
                        Log.d(TAG, "Call ${session.scheduledCallId} went off-hook")
                        startSpeakerphoneLocked(appContext, session)
                    }
                    null
                }

                TelephonyManager.CALL_STATE_IDLE -> {
                    when (session.phase) {
                        CallWatchPhase.IN_CALL -> {
                            stopSpeakerphoneLocked()
                            stopWatchTimeoutLocked()
                            val startedAt = session.offHookAtMillis ?: session.placedAtMillis
                            ((now - startedAt) / 1_000L).toInt().coerceAtLeast(0)
                        }

                        CallWatchPhase.WAITING_OFFHOOK -> {
                            stopSpeakerphoneLocked()
                            if (now - session.placedAtMillis < STALE_IDLE_GRACE_MS) {
                                null
                            } else {
                                stopWatchTimeoutLocked()
                                0
                            }
                        }

                        CallWatchPhase.RETRY_PENDING -> null
                    }
                }

                else -> null
            }
        }

        if (durationToEvaluate != null) {
            evaluateEndedCall(appContext, durationToEvaluate)
        }
    }

    private fun evaluateEndedCall(context: Context, actualDurationSeconds: Int) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val session = CallWatchStore.load(appContext) ?: return
            if (session.phase == CallWatchPhase.RETRY_PENDING) return

            recordDuration(appContext, session.scheduledCallId, actualDurationSeconds)

            if (session.expectedDurationSeconds <= 0) {
                Log.d(
                    TAG,
                    "Call ${session.scheduledCallId} lasted ${actualDurationSeconds}s (duration tracking only)",
                )
                stopSpeakerphoneLocked()
                stopWatchTimeoutLocked()
                stopMonitorLocked()
                CallWatchStore.clear(appContext)
                return
            }

            val settings = AppSettings(appContext).getRetrySettings()
            val delta = abs(actualDurationSeconds - session.expectedDurationSeconds)
            val withinWindow = delta <= session.successWindowSeconds

            Log.d(
                TAG,
                "Call ${session.scheduledCallId} lasted ${actualDurationSeconds}s " +
                    "(expected ${session.expectedDurationSeconds}s ±${session.successWindowSeconds}s)",
            )

            if (withinWindow) {
                AlarmScheduler(appContext).cancelRetry(session.scheduledCallId)
                stopSpeakerphoneLocked()
                stopWatchTimeoutLocked()
                stopMonitorLocked()
                CallWatchStore.clear(appContext)
                Log.d(TAG, "Call ${session.scheduledCallId} matched expected duration")
                return
            }

            if (isPastRetryDeadline(session, settings, System.currentTimeMillis() + RETRY_DELAY_MS)) {
                giveUpLocked(appContext, session, "past retry deadline")
                return
            }

            if (session.retryAttempt < settings.maxRetries) {
                val next = session.copy(
                    retryAttempt = session.retryAttempt + 1,
                    phase = CallWatchPhase.RETRY_PENDING,
                    offHookAtMillis = null,
                )
                CallWatchStore.save(appContext, next)
                val scheduled = AlarmScheduler(appContext).scheduleRetry(
                    scheduledCallId = next.scheduledCallId,
                    dayOfWeek = next.dayOfWeek,
                    retryAttempt = next.retryAttempt,
                    delayMs = RETRY_DELAY_MS,
                )
                if (!scheduled) {
                    giveUpLocked(appContext, session, "failed to schedule retry")
                }
            } else {
                giveUpLocked(appContext, session, "max retries reached")
            }
        }
    }

    private fun giveUpLocked(appContext: Context, session: CallWatchSession, reason: String) {
        AlarmScheduler(appContext).cancelRetry(session.scheduledCallId)
        stopSpeakerphoneLocked()
        stopWatchTimeoutLocked()
        stopMonitorLocked()
        CallWatchStore.clear(appContext)
        FailureNotifier.show(appContext, session)
        Log.w(TAG, "Giving up on call ${session.scheduledCallId}: $reason")
    }

    private fun recordDuration(context: Context, scheduledCallId: Long, durationSeconds: Int) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val dao = AppDatabase.getInstance(context).callDurationLogDao()
                dao.insert(
                    CallDurationLog(
                        scheduledCallId = scheduledCallId,
                        durationSeconds = durationSeconds,
                        recordedAtMillis = System.currentTimeMillis(),
                    ),
                )
                val ids = dao.idsForCall(scheduledCallId)
                if (ids.size > CallDurationLog.MAX_RECENT) {
                    dao.deleteByIds(ids.drop(CallDurationLog.MAX_RECENT))
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to record call duration", error)
            }
        }
    }

    private fun startWatchTimeout(
        context: Context,
        scheduledCallId: Long,
        placedAtMillis: Long,
        timeoutSeconds: Int,
    ) {
        watchTimeoutJob?.cancel()
        watchTimeoutJob = scope.launch {
            delay(timeoutSeconds.coerceAtLeast(15) * 1_000L)
            val current = synchronized(lock) { CallWatchStore.load(context) } ?: return@launch
            if (current.scheduledCallId != scheduledCallId) return@launch
            if (current.placedAtMillis != placedAtMillis) return@launch
            if (current.phase != CallWatchPhase.WAITING_OFFHOOK) return@launch
            Log.w(TAG, "Call ${current.scheduledCallId} never connected; treating as failed")
            evaluateEndedCall(context, actualDurationSeconds = 0)
        }
    }

    private fun stopWatchTimeoutLocked() {
        watchTimeoutJob?.cancel()
        watchTimeoutJob = null
    }

    private fun startSpeakerphoneLocked(context: Context, session: CallWatchSession) {
        if (!session.useSpeakerphone) return
        val appContext = context.applicationContext
        speakerphoneJob?.cancel()
        speakerphoneJob = scope.launch {
            repeat(SPEAKERPHONE_RETRY_COUNT) {
                SpeakerphoneHelper.enable(appContext)
                delay(SPEAKERPHONE_RETRY_DELAY_MS)
            }
            SpeakerphoneHelper.enable(appContext)
        }
    }

    private fun stopSpeakerphoneLocked() {
        speakerphoneJob?.cancel()
        speakerphoneJob = null
    }

    private fun isPastRetryDeadline(
        session: CallWatchSession,
        settings: RetrySettings,
        atMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val deadlineMillis = session.originalScheduleMillis +
            settings.retryDeadlineMinutes * 60_000L
        return atMillis >= deadlineMillis
    }

    private fun originalScheduleMillis(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val scheduled = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        var delta = dayOfWeek - currentDay
        if (delta > 3) delta -= 7
        if (delta < -3) delta += 7
        scheduled.add(Calendar.DAY_OF_YEAR, delta)
        if (scheduled.timeInMillis > now.timeInMillis + TWELVE_HOURS_MS) {
            scheduled.add(Calendar.DAY_OF_YEAR, -7)
        }
        return scheduled.timeInMillis
    }

    private fun ensureMonitorLocked(context: Context) {
        val startMonitor = {
            synchronized(lock) {
                if (monitor == null && CallWatchStore.load(context) != null) {
                    monitor = CallStateMonitor(context) { state ->
                        onPhoneState(context, state)
                    }.also { it.start() }
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (monitor != null) return
            monitor = CallStateMonitor(context) { state ->
                onPhoneState(context, state)
            }.also { it.start() }
        } else {
            mainHandler.post(startMonitor)
        }
    }

    private fun stopMonitorLocked() {
        val current = monitor
        monitor = null
        if (current == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            current.stop()
        } else {
            mainHandler.post { current.stop() }
        }
    }

    private const val TAG = "CallRetryCoordinator"
    private const val RETRY_DELAY_MS = 3_000L
    private const val STALE_IDLE_GRACE_MS = 1_500L
    private const val TWELVE_HOURS_MS = 12 * 60 * 60 * 1000L
    private const val DEFAULT_DURATION_ONLY_TIMEOUT_SECONDS = 30 * 60
    private const val SPEAKERPHONE_RETRY_COUNT = 6
    private const val SPEAKERPHONE_RETRY_DELAY_MS = 1_000L
}
