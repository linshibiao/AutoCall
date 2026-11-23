package com.autocall.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.autocall.app.data.AppDatabase
import com.autocall.app.data.AppSettings
import com.autocall.app.data.CallDurationLog
import com.autocall.app.data.RetrySettings
import com.autocall.app.data.ScheduledCall
import com.autocall.app.scheduler.AlarmScheduler
import com.autocall.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

object CallRetryCoordinator {

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitor: CallStateMonitor? = null
    private var speakerphoneJob: Job? = null
    private var watchTimeoutJob: Job? = null
    private var endCallJob: Job? = null

    fun restore(context: Context) {
        val appContext = context.applicationContext
        val hasSession = synchronized(lock) {
            val session = CallWatchStore.load(appContext)
            if (session != null) {
                ensureMonitorLocked(appContext)
                // Process death drops in-memory jobs; reschedule auto-hangup with remaining time.
                if (session.phase == CallWatchPhase.IN_CALL && session.endCallAfterSeconds > 0) {
                    startEndCallTimerLocked(appContext, session)
                }
            }
            session != null
        }
        ScreenTimeoutHelper.syncWithWatchSession(appContext, hasActiveWatchSession = hasSession)
    }

    /**
     * True when dialing now would interrupt another call or steal another call's watch session.
     * [forcePlace] skips the telephony busy check (user chose Place) but still blocks when a
     * different AutoCall watch session owns the slot.
     */
    fun shouldDeferDial(
        context: Context,
        scheduledCallId: Long,
        retryAttempt: Int,
        forcePlace: Boolean = false,
    ): Boolean {
        val appContext = context.applicationContext
        val activeSession = synchronized(lock) {
            CallWatchStore.load(appContext)?.let {
                BusyDeferLogic.ActiveWatchSnapshot(
                    scheduledCallId = it.scheduledCallId,
                    phase = it.phase,
                )
            }
        }
        return BusyDeferLogic.shouldDeferDial(
            lineBusy = isLineBusy(appContext),
            forcePlace = forcePlace,
            activeSession = activeSession,
            scheduledCallId = scheduledCallId,
            retryAttempt = retryAttempt,
        )
    }

    /**
     * Starts a new watch session. Returns false if a *different* call's session is already
     * active. Same-call sessions are reclaimed (stale/orphan after process death).
     */
    fun beginSession(
        context: Context,
        scheduledCall: ScheduledCall,
        dayOfWeek: Int,
        isTestCall: Boolean = false,
    ): Boolean {
        val expected = scheduledCall.expectedDurationSeconds?.takeIf { it > 0 } ?: 0

        val appContext = context.applicationContext
        synchronized(lock) {
            CallWatchStore.load(appContext)?.let { existing ->
                if (existing.scheduledCallId != scheduledCall.id) {
                    Log.w(
                        TAG,
                        "Refusing to begin session for call ${scheduledCall.id}; " +
                            "active session ${existing.scheduledCallId} phase=${existing.phase}",
                    )
                    return false
                }
                AlarmScheduler(appContext).cancelRetry(existing.scheduledCallId)
                Log.i(
                    TAG,
                    "Reclaiming watch session for call ${scheduledCall.id} (was ${existing.phase})",
                )
            }
            AlarmScheduler(appContext).cancelRetry(scheduledCall.id)
            val now = System.currentTimeMillis()
            CallWatchStore.save(
                appContext,
                CallWatchSession(
                    scheduledCallId = scheduledCall.id,
                    dayOfWeek = dayOfWeek,
                    retryAttempt = 0,
                    expectedDurationSeconds = expected,
                    phase = CallWatchPhase.RETRY_PENDING,
                    // Test dials start the retry deadline window from now, not the schedule clock.
                    originalScheduleMillis = if (isTestCall) {
                        now
                    } else {
                        originalScheduleMillis(
                            dayOfWeek = dayOfWeek,
                            hour = scheduledCall.hour,
                            minute = scheduledCall.minute,
                        )
                    },
                    placedAtMillis = now,
                    offHookAtMillis = null,
                    contactName = scheduledCall.contactName,
                    phoneNumber = scheduledCall.phoneNumber,
                    useSpeakerphone = scheduledCall.useSpeakerphone,
                    successWindowSeconds = scheduledCall.successWindowSeconds.coerceIn(
                        ScheduledCall.MIN_SUCCESS_WINDOW_SECONDS,
                        ScheduledCall.MAX_SUCCESS_WINDOW_SECONDS,
                    ),
                    endCallAfterSeconds = ScheduledCall.normalizeEndCallAfterSeconds(
                        endCallAfterSeconds = scheduledCall.endCallAfterSeconds,
                        expectedDurationSeconds = scheduledCall.expectedDurationSeconds,
                        successWindowSeconds = scheduledCall.successWindowSeconds,
                    ) ?: 0,
                    isTestCall = isTestCall,
                ),
            )
            ensureMonitorLocked(appContext)
            return true
        }
    }

    fun onCallPlaced(context: Context) {
        val appContext = context.applicationContext
        val timeoutSeconds: Int
        val sessionId: Long
        val placedAt: Long
        val alreadyOffHook: Boolean
        synchronized(lock) {
            val session = CallWatchStore.load(appContext) ?: return
            val placedAtMillis = System.currentTimeMillis()
            // Taking over / dialing while already off-hook never delivers a second OFFHOOK
            // transition, so start timing immediately or duration + redial never run.
            alreadyOffHook = currentCallState(appContext) == TelephonyManager.CALL_STATE_OFFHOOK
            if (alreadyOffHook) {
                val inCall = session.copy(
                    phase = CallWatchPhase.IN_CALL,
                    placedAtMillis = placedAtMillis,
                    offHookAtMillis = placedAtMillis,
                    endedByAutoHangup = false,
                )
                CallWatchStore.save(appContext, inCall)
                startSpeakerphoneLocked(appContext, inCall)
                startEndCallTimerLocked(appContext, inCall)
                Log.d(TAG, "Call ${session.scheduledCallId} already off-hook; timing from place")
            } else {
                CallWatchStore.save(
                    appContext,
                    session.copy(
                        phase = CallWatchPhase.WAITING_OFFHOOK,
                        placedAtMillis = placedAtMillis,
                        offHookAtMillis = null,
                        endedByAutoHangup = false,
                    ),
                )
            }
            ensureMonitorLocked(appContext)
            timeoutSeconds = if (session.expectedDurationSeconds > 0) {
                session.expectedDurationSeconds + session.successWindowSeconds + 15
            } else {
                DEFAULT_DURATION_ONLY_TIMEOUT_SECONDS
            }
            sessionId = session.scheduledCallId
            placedAt = placedAtMillis
        }
        if (!alreadyOffHook) {
            startWatchTimeout(appContext, sessionId, placedAt, timeoutSeconds)
        }
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
            stopEndCallTimerLocked()
            stopWatchTimeoutLocked()
            stopMonitorLocked()
            CallWatchStore.clear(appContext)
            if (session != null && session.retryAttempt > 0) {
                FailureNotifier.show(appContext, session)
                Log.w(TAG, "Aborted call ${session.scheduledCallId} after retries; notified user")
            }
        }
        ScreenTimeoutHelper.release(appContext)
    }

    /** Clears the watch session only when it belongs to [scheduledCallId]. */
    fun abortIfSessionFor(context: Context, scheduledCallId: Long) {
        val appContext = context.applicationContext
        val matches = synchronized(lock) {
            CallWatchStore.load(appContext)?.scheduledCallId == scheduledCallId
        }
        if (matches) {
            abort(appContext)
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
                        val inCall = session.copy(
                            phase = CallWatchPhase.IN_CALL,
                            offHookAtMillis = now,
                            endedByAutoHangup = false,
                        )
                        CallWatchStore.save(appContext, inCall)
                        Log.d(TAG, "Call ${session.scheduledCallId} went off-hook")
                        startSpeakerphoneLocked(appContext, inCall)
                        startEndCallTimerLocked(appContext, inCall)
                    }
                    null
                }

                TelephonyManager.CALL_STATE_IDLE -> {
                    when (session.phase) {
                        CallWatchPhase.IN_CALL -> {
                            stopSpeakerphoneLocked()
                            stopEndCallTimerLocked()
                            stopWatchTimeoutLocked()
                            val startedAt = session.offHookAtMillis ?: session.placedAtMillis
                            ((now - startedAt) / 1_000L).toInt().coerceAtLeast(0)
                        }

                        CallWatchPhase.WAITING_OFFHOOK -> {
                            stopSpeakerphoneLocked()
                            stopEndCallTimerLocked()
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

            recordDuration(
                appContext,
                session.scheduledCallId,
                actualDurationSeconds,
                endedByAutoHangup = session.endedByAutoHangup,
            )

            if (session.expectedDurationSeconds <= 0) {
                Log.d(
                    TAG,
                    "Call ${session.scheduledCallId} lasted ${actualDurationSeconds}s (duration tracking only)",
                )
                finishCampaignLocked(appContext)
                return
            }

            val settings = AppSettings(appContext).getRetrySettings()
            val successful = CallOutcomeLogic.isSuccessful(
                actualDurationSeconds = actualDurationSeconds,
                expectedDurationSeconds = session.expectedDurationSeconds,
                successWindowSeconds = session.successWindowSeconds,
                endedByAutoHangup = session.endedByAutoHangup,
            )

            Log.d(
                TAG,
                "Call ${session.scheduledCallId} lasted ${actualDurationSeconds}s " +
                    "(expected ${session.expectedDurationSeconds}s ±${session.successWindowSeconds}s)" +
                    if (session.endedByAutoHangup) " [auto-hangup]" else "",
            )

            if (successful) {
                AlarmScheduler(appContext).cancelRetry(session.scheduledCallId)
                finishCampaignLocked(appContext)
                Log.d(TAG, "Call ${session.scheduledCallId} matched expected duration")
                return
            }

            if (session.endedByAutoHangup) {
                Log.i(
                    TAG,
                    "Call ${session.scheduledCallId} ended by auto-hangup; treating as failure",
                )
            }

            val retryDelayMs = settings.retryDelaySeconds * 1_000L
            if (isPastRetryDeadline(session, settings, System.currentTimeMillis() + retryDelayMs)) {
                giveUpLocked(appContext, session, "past retry deadline")
                return
            }

            if (session.retryAttempt < settings.maxRetries) {
                val next = session.copy(
                    retryAttempt = session.retryAttempt + 1,
                    phase = CallWatchPhase.RETRY_PENDING,
                    offHookAtMillis = null,
                    endedByAutoHangup = false,
                )
                CallWatchStore.save(appContext, next)
                val scheduled = AlarmScheduler(appContext).scheduleRetry(
                    scheduledCallId = next.scheduledCallId,
                    dayOfWeek = next.dayOfWeek,
                    retryAttempt = next.retryAttempt,
                    delayMs = retryDelayMs,
                    isTestCall = next.isTestCall,
                )
                if (!scheduled) {
                    giveUpLocked(appContext, session, "failed to schedule retry")
                }
            } else {
                giveUpLocked(appContext, session, "max retries reached")
            }
        }
    }

    private fun finishCampaignLocked(appContext: Context) {
        stopSpeakerphoneLocked()
        stopEndCallTimerLocked()
        stopWatchTimeoutLocked()
        stopMonitorLocked()
        CallWatchStore.clear(appContext)
        ScreenTimeoutHelper.release(appContext)
    }

    private fun giveUpLocked(appContext: Context, session: CallWatchSession, reason: String) {
        AlarmScheduler(appContext).cancelRetry(session.scheduledCallId)
        finishCampaignLocked(appContext)
        FailureNotifier.show(appContext, session)
        Log.w(TAG, "Giving up on call ${session.scheduledCallId}: $reason")
    }

    private fun recordDuration(
        context: Context,
        scheduledCallId: Long,
        durationSeconds: Int,
        endedByAutoHangup: Boolean,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val dao = AppDatabase.getInstance(context).callDurationLogDao()
                dao.insert(
                    CallDurationLog(
                        scheduledCallId = scheduledCallId,
                        durationSeconds = durationSeconds,
                        recordedAtMillis = System.currentTimeMillis(),
                        endedByAutoHangup = endedByAutoHangup,
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

    private fun startEndCallTimerLocked(context: Context, session: CallWatchSession) {
        stopEndCallTimerLocked()
        val endAfterSeconds = session.endCallAfterSeconds
        val offHookAt = session.offHookAtMillis ?: return
        val delayMs = EndCallTimerLogic.remainingDelayMs(
            endCallAfterSeconds = endAfterSeconds,
            offHookAtMillis = offHookAt,
            nowMillis = System.currentTimeMillis(),
        ) ?: return
        val scheduledCallId = session.scheduledCallId
        val appContext = context.applicationContext
        endCallJob = scope.launch {
            delay(delayMs)
            val marked = synchronized(lock) {
                val current = CallWatchStore.load(appContext) ?: return@synchronized false
                if (current.scheduledCallId != scheduledCallId) return@synchronized false
                if (current.phase != CallWatchPhase.IN_CALL) return@synchronized false
                if (current.offHookAtMillis != offHookAt) return@synchronized false
                // Natural hangup in the same window should not be labeled auto-hangup.
                if (currentCallState(appContext) != TelephonyManager.CALL_STATE_OFFHOOK) {
                    return@synchronized false
                }
                CallWatchStore.save(appContext, current.copy(endedByAutoHangup = true))
                true
            }
            if (!marked) return@launch
            Log.i(
                TAG,
                "Auto-ending call $scheduledCallId after ${endAfterSeconds}s " +
                    "(timer delay ${delayMs}ms)",
            )
            val ended = CallEnder.endCall(appContext)
            if (!ended) {
                // Only clear if the call is still active; otherwise IDLE may already be
                // evaluating and must keep the forced-failure flag.
                val stillActive = currentCallState(appContext) == TelephonyManager.CALL_STATE_OFFHOOK
                synchronized(lock) {
                    val current = CallWatchStore.load(appContext) ?: return@synchronized
                    if (current.scheduledCallId != scheduledCallId) return@synchronized
                    if (current.phase != CallWatchPhase.IN_CALL) return@synchronized
                    if (!stillActive) return@synchronized
                    CallWatchStore.save(appContext, current.copy(endedByAutoHangup = false))
                }
                Log.w(TAG, "Auto-hangup failed for call $scheduledCallId; cleared failure flag")
            }
        }
    }

    private fun stopEndCallTimerLocked() {
        endCallJob?.cancel()
        endCallJob = null
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
    ): Boolean = BusyDeferLogic.isPastRetryDeadline(
        originalScheduleMillis = session.originalScheduleMillis,
        retryDeadlineMinutes = settings.retryDeadlineMinutes,
        atMillis = atMillis,
    )

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

    fun isLineBusy(context: Context): Boolean {
        val appContext = context.applicationContext
        val state = currentCallState(appContext)
        if (
            state == TelephonyManager.CALL_STATE_OFFHOOK ||
            state == TelephonyManager.CALL_STATE_RINGING
        ) {
            return true
        }
        // Google Voice / other self-managed VoIP can be in-call while TelephonyManager
        // still reports IDLE. Telecom sees managed + self-managed calls.
        return isTelecomInCall(appContext)
    }

    @SuppressLint("MissingPermission")
    private fun currentCallState(context: Context): Int {
        if (!PermissionHelper.hasReadPhoneStatePermission(context)) {
            return TelephonyManager.CALL_STATE_IDLE
        }
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return telephonyManager.callState
    }

    @SuppressLint("MissingPermission")
    private fun isTelecomInCall(context: Context): Boolean {
        if (!PermissionHelper.hasReadPhoneStatePermission(context)) return false
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        return runCatching { telecom.isInCall }.getOrDefault(false)
    }

    private const val TAG = "CallRetryCoordinator"
    const val BUSY_DEFER_DELAY_MS = 10_000L
    private const val STALE_IDLE_GRACE_MS = 1_500L
    private const val TWELVE_HOURS_MS = 12 * 60 * 60 * 1000L
    private const val DEFAULT_DURATION_ONLY_TIMEOUT_SECONDS = 30 * 60
    private const val SPEAKERPHONE_RETRY_COUNT = 6
    private const val SPEAKERPHONE_RETRY_DELAY_MS = 1_000L
}
