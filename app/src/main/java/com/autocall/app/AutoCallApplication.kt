package com.autocall.app

import android.app.Application
import com.autocall.app.call.CallRetryCoordinator
import com.autocall.app.data.AppDatabase
import com.autocall.app.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoCallApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val dao = AppDatabase.getInstance(this@AutoCallApplication).scheduledCallDao()
            AlarmScheduler(this@AutoCallApplication).rescheduleAll(dao)
        }
        CallRetryCoordinator.restore(this)
    }
}
