package com.autocall.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autocall.app.data.AppDatabase
import com.autocall.app.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getInstance(context).scheduledCallDao()
                AlarmScheduler(context).rescheduleAll(dao)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
