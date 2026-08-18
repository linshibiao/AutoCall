package com.autocall.app.call

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.Executor

class CallStateMonitor(
    context: Context,
    private val onStateChanged: (Int) -> Unit,
) {
    private val appContext = context.applicationContext
    private val telephonyManager =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var skipFirstCallback = true

    @SuppressLint("MissingPermission")
    fun start() {
        skipFirstCallback = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = dispatch(state)
            }
            telephonyCallback = callback
            telephonyManager.registerTelephonyCallback(mainExecutor(), callback)
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    dispatch(state)
                }
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { callback ->
                telephonyManager.unregisterTelephonyCallback(callback)
            }
            telephonyCallback = null
        } else {
            phoneStateListener?.let { listener ->
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            }
            phoneStateListener = null
        }
    }

    private fun dispatch(state: Int) {
        if (skipFirstCallback) {
            skipFirstCallback = false
            return
        }
        onStateChanged(state)
    }

    private fun mainExecutor(): Executor =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            appContext.mainExecutor
        } else {
            Executor { runnable -> android.os.Handler(appContext.mainLooper).post(runnable) }
        }
}
