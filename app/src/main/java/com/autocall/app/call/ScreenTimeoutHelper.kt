package com.autocall.app.call

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.autocall.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Temporarily raises [Settings.System.SCREEN_OFF_TIMEOUT] while AutoCall is dialing
 * (including retries), then restores the user's original value when the campaign ends.
 *
 * Requires [Settings.System.canWrite]; without it, acquire is a no-op and release keeps
 * any persisted hold so a later grant can still restore the original timeout.
 */
object ScreenTimeoutHelper {

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var safetyReleaseJob: Job? = null

    fun isHolding(context: Context): Boolean =
        ScreenTimeoutHoldStore.isHolding(context)

    /**
     * Saves the current timeout (once) and sets screen-off to [DIAL_TIMEOUT_MS].
     * Safe to call repeatedly while a dial campaign is in progress.
     */
    fun acquire(context: Context) {
        val appContext = context.applicationContext
        if (!PermissionHelper.canWriteSettings(appContext)) {
            Log.w(TAG, "WRITE_SETTINGS not granted; cannot extend screen timeout")
            return
        }

        synchronized(lock) {
            cancelDeferredReleaseLocked()
            try {
                val current = Settings.System.getInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    FALLBACK_TIMEOUT_MS,
                )
                if (ScreenTimeoutHoldStore.markHolding(appContext, current)) {
                    Log.i(TAG, "Saved original screen timeout ${current}ms")
                }
                if (current != DIAL_TIMEOUT_MS) {
                    val wrote = Settings.System.putInt(
                        appContext.contentResolver,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        DIAL_TIMEOUT_MS,
                    )
                    if (!wrote) {
                        Log.w(TAG, "Failed to write elevated screen timeout")
                    }
                }
                Log.i(TAG, "Screen timeout set to ${DIAL_TIMEOUT_MS}ms for dialing")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to extend screen timeout", error)
            }
        }
    }

    /**
     * Restores the original screen timeout if we previously acquired it.
     * Only clears the persisted hold after a successful restore (or when there is
     * nothing to restore), so a failed restore can be retried after a crash/re-grant.
     */
    fun release(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            cancelDeferredReleaseLocked()
            if (!ScreenTimeoutHoldStore.isHolding(appContext)) return

            val original = ScreenTimeoutHoldStore.originalTimeoutMs(appContext)
            if (original < 0) {
                Log.w(TAG, "No original screen timeout saved; clearing hold flag only")
                ScreenTimeoutHoldStore.clearHolding(appContext)
                return
            }

            if (!PermissionHelper.canWriteSettings(appContext)) {
                Log.w(
                    TAG,
                    "WRITE_SETTINGS missing; keeping hold to restore ${original}ms later",
                )
                return
            }

            try {
                val wrote = Settings.System.putInt(
                    appContext.contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    original,
                )
                if (!wrote) {
                    Log.w(TAG, "Failed to restore screen timeout to ${original}ms; keeping hold")
                    return
                }
                ScreenTimeoutHoldStore.clearHolding(appContext)
                Log.i(TAG, "Restored screen timeout to ${original}ms")
            } catch (error: Exception) {
                Log.e(TAG, "Failed to restore screen timeout; keeping hold", error)
            }
        }
    }

    /**
     * Used when a call is placed without a [CallRetryCoordinator] watch session
     * (no phone-state permission). Releases after [DIAL_TIMEOUT_MS].
     */
    fun releaseWhenCallCompletes(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (!ScreenTimeoutHoldStore.isHolding(appContext)) return
            cancelDeferredReleaseLocked()
            safetyReleaseJob = scope.launch {
                delay(DIAL_TIMEOUT_MS.toLong())
                release(appContext)
            }
        }
    }

    /**
     * On process start: keep the extended timeout if a watch session is still active;
     * otherwise restore any orphaned hold from a prior crash.
     */
    fun syncWithWatchSession(context: Context, hasActiveWatchSession: Boolean) {
        when (
            ScreenTimeoutSyncLogic.plan(
                hasActiveWatchSession = hasActiveWatchSession,
                isHolding = isHolding(context),
            )
        ) {
            ScreenTimeoutSyncLogic.Action.ACQUIRE -> acquire(context)
            ScreenTimeoutSyncLogic.Action.RELEASE -> release(context)
            ScreenTimeoutSyncLogic.Action.NONE -> Unit
        }
    }

    private fun cancelDeferredReleaseLocked() {
        safetyReleaseJob?.cancel()
        safetyReleaseJob = null
    }

    const val DIAL_TIMEOUT_MS = 5 * 60 * 1_000
    private const val FALLBACK_TIMEOUT_MS = 30_000
    private const val TAG = "ScreenTimeoutHelper"
}
