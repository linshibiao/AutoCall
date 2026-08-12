package com.autocall.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.autocall.app.data.AppDatabase
import com.autocall.app.data.ScheduledCallRepository
import com.autocall.app.scheduler.AlarmScheduler

class AutoCallViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getInstance(application)
        val alarmScheduler = AlarmScheduler(application)
        val repository = ScheduledCallRepository(database.scheduledCallDao(), alarmScheduler)
        return AutoCallViewModel(application, repository) as T
    }
}
