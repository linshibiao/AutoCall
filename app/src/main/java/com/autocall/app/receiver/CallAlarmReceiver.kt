package com.autocall.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.autocall.app.data.AppDatabase
import com.autocall.app.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_CALL) return

        val scheduledCallId = intent.getLongExtra(EXTRA_SCHEDULED_CALL_ID, -1L)
        if (scheduledCallId < 0) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleScheduledCall(context, scheduledCallId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleScheduledCall(context: Context, scheduledCallId: Long) {
        val dao = AppDatabase.getInstance(context).scheduledCallDao()
        val scheduledCall = dao.getById(scheduledCallId) ?: return
        if (!scheduledCall.isEnabled) return

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${Uri.encode(scheduledCall.phoneNumber)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(callIntent)

        if (scheduledCall.useSpeakerphone) {
            enableSpeakerphoneAfterDelay(context)
        }

        AlarmScheduler(context).scheduleCall(scheduledCall)
    }

    private fun enableSpeakerphoneAfterDelay(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Handler(Looper.getMainLooper()).postDelayed({
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }, SPEAKERPHONE_DELAY_MS)
    }

    companion object {
        const val ACTION_TRIGGER_CALL = "com.autocall.app.ACTION_TRIGGER_CALL"
        const val EXTRA_SCHEDULED_CALL_ID = "extra_scheduled_call_id"
        private const val SPEAKERPHONE_DELAY_MS = 2_000L
    }
}
