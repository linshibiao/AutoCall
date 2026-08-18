package com.autocall.app.call

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

object CallLauncher {

    fun buildTelUri(phoneNumber: String): Uri =
        Uri.fromParts("tel", phoneNumber, null)

    fun placeCall(
        context: Context,
        phoneNumber: String,
        useSpeakerphone: Boolean = false,
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val uri = buildTelUri(phoneNumber)
        val extras = Bundle().apply {
            if (useSpeakerphone) {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true)
            }
        }

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
        }
        context.startActivity(callIntent)
        return true
    }
}
