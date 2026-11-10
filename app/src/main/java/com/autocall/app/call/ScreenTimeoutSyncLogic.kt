package com.autocall.app.call

/**
 * Pure decision logic for reconciling a persisted screen-timeout hold with an
 * in-memory/watch-session restore.
 */
object ScreenTimeoutSyncLogic {

    enum class Action {
        ACQUIRE,
        RELEASE,
        NONE,
    }

    fun plan(hasActiveWatchSession: Boolean, isHolding: Boolean): Action = when {
        hasActiveWatchSession -> Action.ACQUIRE
        isHolding -> Action.RELEASE
        else -> Action.NONE
    }
}
