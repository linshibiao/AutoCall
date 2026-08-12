package com.autocall.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun AutoCallApp(viewModel: AutoCallViewModel) {
    val scheduledCalls by viewModel.scheduledCalls.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val editingCall by viewModel.editingCall.collectAsState()

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
    )

    editingCall?.let { call ->
        AddEditCallDialog(
            existingCall = if (call.id == 0L) null else call,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveCall,
        )
    }
}
