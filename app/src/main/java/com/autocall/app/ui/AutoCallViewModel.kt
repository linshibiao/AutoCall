package com.autocall.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autocall.app.data.AppSettings
import com.autocall.app.data.RetrySettings
import com.autocall.app.data.ScheduledCall
import com.autocall.app.data.ScheduledCallRepository
import com.autocall.app.util.DaysOfWeek
import com.autocall.app.util.PermissionHelper
import com.autocall.app.util.sortedByNextTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SystemStatus(
    val hasCallPhonePermission: Boolean = false,
    val hasReadPhoneStatePermission: Boolean = false,
    val hasReadContactsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
) {
    val hasAllRuntimePermissions: Boolean
        get() = hasCallPhonePermission &&
            hasReadPhoneStatePermission &&
            hasReadContactsPermission &&
            hasNotificationPermission

    val needsAttention: Boolean
        get() = !hasAllRuntimePermissions ||
            !canScheduleExactAlarms ||
            !isIgnoringBatteryOptimizations
}

class AutoCallViewModel(
    application: Application,
    private val repository: ScheduledCallRepository,
    private val appSettings: AppSettings = AppSettings(application),
) : AndroidViewModel(application) {

    val scheduledCalls = repository.scheduledCalls
        .map { calls -> calls.sortedByNextTrigger() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _systemStatus = MutableStateFlow(SystemStatus())
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val _editingCall = MutableStateFlow<ScheduledCall?>(null)
    val editingCall: StateFlow<ScheduledCall?> = _editingCall.asStateFlow()

    private val _retrySettings = MutableStateFlow(appSettings.getRetrySettings())
    val retrySettings: StateFlow<RetrySettings> = _retrySettings.asStateFlow()

    fun refreshSystemStatus() {
        val context = getApplication<Application>()
        _systemStatus.value = SystemStatus(
            hasCallPhonePermission = PermissionHelper.hasCallPhonePermission(context),
            hasReadPhoneStatePermission = PermissionHelper.hasReadPhoneStatePermission(context),
            hasReadContactsPermission = PermissionHelper.hasReadContactsPermission(context),
            hasNotificationPermission = PermissionHelper.hasNotificationPermission(context),
            canScheduleExactAlarms = PermissionHelper.canScheduleExactAlarms(context),
            isIgnoringBatteryOptimizations = PermissionHelper.isIgnoringBatteryOptimizations(context),
        )
        if (_systemStatus.value.hasAllRuntimePermissions &&
            _systemStatus.value.canScheduleExactAlarms
        ) {
            viewModelScope.launch {
                repository.rescheduleAllEnabled()
            }
        }
    }

    fun startAddingCall() {
        val now = java.util.Calendar.getInstance()
        _editingCall.value = ScheduledCall(
            phoneNumber = "",
            daysOfWeek = DaysOfWeek.encode(setOf(now.get(java.util.Calendar.DAY_OF_WEEK))),
            hour = now.get(java.util.Calendar.HOUR_OF_DAY),
            minute = now.get(java.util.Calendar.MINUTE),
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
                daysOfWeek = DaysOfWeek.encode(form.daysOfWeek),
                hour = form.hour,
                minute = form.minute,
                isEnabled = form.isEnabled,
                useSpeakerphone = form.useSpeakerphone,
                expectedDurationSeconds = form.expectedDurationSeconds,
            )

            if (call.phoneNumber.isBlank() || call.days().isEmpty()) return@launch

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

    fun updateRetrySettings(toleranceSeconds: Int, maxRetries: Int, retryDeadlineMinutes: Int) {
        _retrySettings.value = appSettings.setRetrySettings(
            toleranceSeconds = toleranceSeconds,
            maxRetries = maxRetries,
            retryDeadlineMinutes = retryDeadlineMinutes,
        )
    }
}

data class ScheduledCallForm(
    val id: Long = 0,
    val contactName: String? = null,
    val phoneNumber: String = "",
    val daysOfWeek: Set<Int> = setOf(java.util.Calendar.MONDAY),
    val hour: Int = 9,
    val minute: Int = 0,
    val isEnabled: Boolean = true,
    val useSpeakerphone: Boolean = false,
    val expectedDurationSeconds: Int? = null,
)
