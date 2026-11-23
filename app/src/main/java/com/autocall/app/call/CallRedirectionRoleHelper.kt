package com.autocall.app.call

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Helpers for the system [RoleManager.ROLE_CALL_REDIRECTION] role (API 29+).
 *
 * The request intent **must** be launched with [android.app.Activity.startActivityForResult]
 * (or the Activity Result API). Plain [Context.startActivity] often shows nothing—the system
 * role UI finishes immediately with [android.app.Activity.RESULT_CANCELED].
 */
object CallRedirectionRoleHelper {

    fun isRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION)
    }

    fun holdsCallRedirectionRole(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)
    }

    /**
     * System UI prompt to make AutoCall the call-redirection app (replacing Google Voice).
     * Null only when the platform cannot request the role (pre-Q / no RoleManager).
     * Still returns an intent when the role reports unavailable—caller should use
     * startActivityForResult; a canceled result means the app is not eligible or the
     * user dismissed.
     */
    fun createRequestRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (holdsCallRedirectionRole(context)) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        val available = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION)
        Log.i(TAG, "createRequestRoleIntent available=$available")
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
    }

    /** Fallback: open Default apps settings so the user can change Call redirection manually. */
    fun createDefaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val TAG = "CallRedirectionRole"
}
