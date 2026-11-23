package com.autocall.app.call

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

object CallLauncher {

    fun buildTelUri(phoneNumber: String): Uri =
        Uri.fromParts("tel", phoneNumber, null)

    fun placeCall(
        context: Context,
        phoneNumber: String,
        useSpeakerphone: Boolean = false,
        forceSimDial: Boolean = false,
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val uri = buildTelUri(phoneNumber)
        val simHandle = if (forceSimDial) {
            SimDialAccountResolver.resolveSimHandle(context).also { handle ->
                if (handle == null) {
                    // Fail closed: placing without a pin would allow GV/default routing.
                    Log.e(TAG, "forceSimDial set but no SIM account found; aborting placeCall")
                }
            }
        } else {
            null
        }
        if (forceSimDial && simHandle == null) {
            Toast.makeText(
                context.applicationContext,
                "SIM dial is on, but no cellular account was found",
                Toast.LENGTH_LONG,
            ).show()
            return false
        }

        val extras = Bundle().apply {
            if (useSpeakerphone) {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
            }
            if (simHandle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simHandle)
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = context.getSystemService(TelecomManager::class.java)
                if (telecomManager != null) {
                    telecomManager.placeCall(uri, extras)
                    return true
                }
            }

            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, useSpeakerphone)
                putPhoneAccountHandleExtra(this, simHandle)
            }
            context.startActivity(callIntent)
            true
        } catch (error: Exception) {
            Log.e(TAG, "placeCall failed", error)
            false
        }
    }

    private fun putPhoneAccountHandleExtra(intent: Intent, handle: PhoneAccountHandle?) {
        if (handle == null) return
        intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
    }

    private const val TAG = "CallLauncher"
}
