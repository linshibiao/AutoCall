package com.autocall.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.TelecomManager
import android.util.Log
import com.autocall.app.util.PermissionHelper

object CallEnder {

    @SuppressLint("MissingPermission")
    fun endCall(context: Context): Boolean {
        if (!PermissionHelper.hasAnswerPhoneCallsPermission(context)) {
            Log.w(TAG, "Cannot end call: ANSWER_PHONE_CALLS not granted")
            return false
        }
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        if (telecomManager == null) {
            Log.w(TAG, "Cannot end call: TelecomManager unavailable")
            return false
        }
        return try {
            @Suppress("DEPRECATION")
            val ended = telecomManager.endCall()
            if (!ended) {
                Log.w(TAG, "TelecomManager.endCall() returned false")
            }
            ended
        } catch (error: SecurityException) {
            Log.e(TAG, "TelecomManager.endCall() denied", error)
            false
        }
    }

    private const val TAG = "CallEnder"
}
