package com.autocall.app.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

object SpeakerphoneHelper {

    fun enable(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices.firstOrNull { device ->
                device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speaker != null && audioManager.setCommunicationDevice(speaker)) {
                return
            }
        }

        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true
    }
}
