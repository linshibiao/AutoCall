package com.autocall.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun AutoCallApp(viewModel: AutoCallViewModel) {
    val scheduledCalls by viewModel.scheduledCalls.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val editingCall by viewModel.editingCall.collectAsState()
    val retrySettings by viewModel.retrySettings.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshSystemStatus()
        onPauseOrDispose { }
    }

    DashboardScreen(
        scheduledCalls = scheduledCalls,
        systemStatus = systemStatus,
        onRefreshStatus = viewModel::refreshSystemStatus,
        onAddClick = viewModel::startAddingCall,
        onEditClick = viewModel::startEditingCall,
        onDeleteClick = viewModel::deleteCall,
        onToggleEnabled = viewModel::toggleEnabled,
        onSettingsClick = { showSettings = true },
    )

    if (showSettings) {
        SettingsDialog(
            retrySettings = retrySettings,
            onDismiss = { showSettings = false },
            onSave = { tolerance, retries, deadlineMinutes ->
                viewModel.updateRetrySettings(tolerance, retries, deadlineMinutes)
                showSettings = false
            },
        )
    }

    editingCall?.let { call ->
        AddEditCallDialog(
            existingCall = if (call.id == 0L) null else call,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveCall,
        )
    }
}
