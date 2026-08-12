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

    fun placeCall(context: Context, phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val uri = buildTelUri(phoneNumber)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            if (telecomManager != null) {
                telecomManager.placeCall(uri, Bundle.EMPTY)
                return true
            }
        }

        val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)
        return true
    }
}
