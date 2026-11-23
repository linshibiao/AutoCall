package com.autocall.app.ui

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autocall.app.call.CallRedirectionRoleHelper
import com.autocall.app.data.AppSettings
import com.autocall.app.data.CallDurationLog
import com.autocall.app.data.RetrySettings
import com.autocall.app.data.ScheduledCall
import com.autocall.app.data.ScheduledCallRepository
import com.autocall.app.service.CallTriggerService
import com.autocall.app.util.DaysOfWeek
import com.autocall.app.util.PermissionHelper
import com.autocall.app.util.RecentCallDuration
import com.autocall.app.util.sortedByNextTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ScheduledCallItem(
    val scheduledCall: ScheduledCall,
    val recentCalls: List<RecentCallDuration> = emptyList(),
)

data class SystemStatus(
    val hasCallPhonePermission: Boolean = false,
    val hasAnswerPhoneCallsPermission: Boolean = false,
    val hasReadPhoneStatePermission: Boolean = false,
    val hasReadContactsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val isIgnoringBatteryOptimizations: Boolean = true,
    val canWriteSettings: Boolean = true,
    val forceSimDialEnabled: Boolean = false,
    val holdsCallRedirectionRole: Boolean = true,
    val callRedirectionRoleAvailable: Boolean = false,
) {
    val hasAllRuntimePermissions: Boolean
        get() = hasCallPhonePermission &&
            hasAnswerPhoneCallsPermission &&
            hasReadPhoneStatePermission &&
            hasReadContactsPermission &&
            hasNotificationPermission

    /**
     * Optional fallback only. Primary SIM bypass uses gateway-tagged dials and does not
     * require holding the call-redirection role (so Google Voice can keep it for other calls).
     */
    val needsCallRedirectionRole: Boolean
        get() = false

    val needsAttention: Boolean
        get() = !hasAllRuntimePermissions ||
            !canScheduleExactAlarms ||
            !isIgnoringBatteryOptimizations ||
            !canWriteSettings
}

class AutoCallViewModel(
    application: Application,
    private val repository: ScheduledCallRepository,
    private val appSettings: AppSettings = AppSettings(application),
) : AndroidViewModel(application) {

    val scheduledCalls = combine(
        repository.scheduledCalls,
        repository.durationLogs,
    ) { calls, logs ->
        val recentByCallId = logs
            .groupBy { log -> log.scheduledCallId }
            .mapValues { (_, entries) ->
                entries
                    .sortedByDescending { log -> log.recordedAtMillis }
                    .take(CallDurationLog.MAX_RECENT)
                    .map { log ->
                        RecentCallDuration(
                            durationSeconds = log.durationSeconds,
                            endedByAutoHangup = log.endedByAutoHangup,
                        )
                    }
            }
        calls.sortedByNextTrigger().map { call ->
            ScheduledCallItem(
                scheduledCall = call,
                recentCalls = recentByCallId[call.id].orEmpty(),
            )
        }
    }.stateIn(
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

    private val _forceSimDial = MutableStateFlow(appSettings.isForceSimDial())
    val forceSimDial: StateFlow<Boolean> = _forceSimDial.asStateFlow()

    fun refreshSystemStatus() {
        val context = getApplication<Application>()
        _systemStatus.value = SystemStatus(
            hasCallPhonePermission = PermissionHelper.hasCallPhonePermission(context),
            hasAnswerPhoneCallsPermission = PermissionHelper.hasAnswerPhoneCallsPermission(context),
            hasReadPhoneStatePermission = PermissionHelper.hasReadPhoneStatePermission(context),
            hasReadContactsPermission = PermissionHelper.hasReadContactsPermission(context),
            hasNotificationPermission = PermissionHelper.hasNotificationPermission(context),
            canScheduleExactAlarms = PermissionHelper.canScheduleExactAlarms(context),
            isIgnoringBatteryOptimizations = PermissionHelper.isIgnoringBatteryOptimizations(context),
            canWriteSettings = PermissionHelper.canWriteSettings(context),
            forceSimDialEnabled = appSettings.isForceSimDial(),
            holdsCallRedirectionRole = CallRedirectionRoleHelper.holdsCallRedirectionRole(context),
            callRedirectionRoleAvailable = CallRedirectionRoleHelper.isRoleAvailable(context),
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
            val successWindowSeconds = form.successWindowSeconds.coerceIn(
                ScheduledCall.MIN_SUCCESS_WINDOW_SECONDS,
                ScheduledCall.MAX_SUCCESS_WINDOW_SECONDS,
            )
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
                successWindowSeconds = successWindowSeconds,
                endCallAfterSeconds = ScheduledCall.normalizeEndCallAfterSeconds(
                    endCallAfterSeconds = form.endCallAfterSeconds,
                    expectedDurationSeconds = form.expectedDurationSeconds,
                    successWindowSeconds = successWindowSeconds,
                ),
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

    fun testCall(scheduledCall: ScheduledCall) {
        if (scheduledCall.id == 0L) return
        val context = getApplication<Application>()
        if (!PermissionHelper.hasCallPhonePermission(context)) {
            refreshSystemStatus()
            Toast.makeText(
                context,
                "Grant Phone permission to place a test call",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        CallTriggerService.start(
            context = context,
            scheduledCallId = scheduledCall.id,
            dayOfWeek = dayOfWeek,
            retryAttempt = 0,
            isTestCall = true,
        )
    }

    fun updateCallSettings(
        maxRetries: Int,
        retryDeadlineMinutes: Int,
        busyDeferPromptMinutes: Int,
        retryDelaySeconds: Int,
        forceSimDial: Boolean,
    ) {
        _retrySettings.value = appSettings.setRetrySettings(
            maxRetries = maxRetries,
            retryDeadlineMinutes = retryDeadlineMinutes,
            busyDeferPromptMinutes = busyDeferPromptMinutes,
            retryDelaySeconds = retryDelaySeconds,
        )
        appSettings.setForceSimDial(forceSimDial)
        _forceSimDial.value = forceSimDial
        refreshSystemStatus()
    }

    /** Intent to make AutoCall the call-redirection app, or null if not needed. */
    fun callRedirectionRoleRequestIntent(): Intent? =
        CallRedirectionRoleHelper.createRequestRoleIntent(getApplication())
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
    val successWindowSeconds: Int = ScheduledCall.DEFAULT_SUCCESS_WINDOW_SECONDS,
    val endCallAfterSeconds: Int? = null,
)
