package com.autocall.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autocall.app.data.ScheduledCall
import com.autocall.app.data.ScheduledCallRepository
import com.autocall.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SystemStatus(
    val hasCallPhonePermission: Boolean = false,
    val hasReadContactsPermission: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
) {
    val hasAllRuntimePermissions: Boolean
        get() = hasCallPhonePermission && hasReadContactsPermission

    val needsAttention: Boolean
        get() = !hasAllRuntimePermissions ||
            !canScheduleExactAlarms ||
            !isIgnoringBatteryOptimizations
}

class AutoCallViewModel(
    application: Application,
    private val repository: ScheduledCallRepository,
) : AndroidViewModel(application) {

    val scheduledCalls = repository.scheduledCalls.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _systemStatus = MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val _editingCall = MutableStateFlow<ScheduledCall?>(null)
    val editingCall: StateFlow<ScheduledCall?> = _editingCall.asStateFlow()

    fun refreshSystemStatus() {
        val context = getApplication<Application>()
        _systemStatus.value = SystemStatus(
            hasCallPhonePermission = PermissionHelper.hasCallPhonePermission(context),
            hasReadContactsPermission = PermissionHelper.hasReadContactsPermission(context),
            canScheduleExactAlarms = PermissionHelper.canScheduleExactAlarms(context),
            isIgnoringBatteryOptimizations = PermissionHelper.isIgnoringBatteryOptimizations(context),
        )
    }

    fun startAddingCall() {
        _editingCall.value = ScheduledCall(
            phoneNumber = "",
            dayOfWeek = java.util.Calendar.MONDAY,
            hour = 9,
            minute = 0,
        )
    }

    fun startEditingCall(scheduledCall: ScheduledCall) {
        _editingCall.value = scheduledCall
    }

    fun dismissEditor() {
        _editingCall.value = null
    }

    fun saveCall(form: ScheduledCallForm) {
        viewModelScope.launch {
            val call = ScheduledCall(
                id = form.id,
                contactName = form.contactName?.takeIf { it.isNotBlank() },
                phoneNumber = form.phoneNumber.trim(),
                dayOfWeek = form.dayOfWeek,
                hour = form.hour,
                minute = form.minute,
                isEnabled = form.isEnabled,
                useSpeakerphone = form.useSpeakerphone,
            )

            if (call.phoneNumber.isBlank()) return@launch

            if (form.id == 0L) {
                repository.insert(call)
            } else {
                repository.update(call)
            }
            _editingCall.value = null
        }
    }

    fun toggleEnabled(scheduledCall: ScheduledCall, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(scheduledCall, enabled)
        }
    }

    fun deleteCall(scheduledCall: ScheduledCall) {
        viewModelScope.launch {
            repository.delete(scheduledCall)
        }
    }
}

data class ScheduledCallForm(
    val id: Long = 0,
    val contactName: String? = null,
    val phoneNumber: String = "",
    val dayOfWeek: Int = java.util.Calendar.MONDAY,
    val hour: Int = 9,
    val minute: Int = 0,
    val isEnabled: Boolean = true,
    val useSpeakerphone: Boolean = false,
)
