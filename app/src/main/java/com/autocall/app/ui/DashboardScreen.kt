package com.autocall.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autocall.app.data.ScheduledCall
import com.autocall.app.scheduler.AlarmScheduler
import com.autocall.app.util.DayOfWeekFormatter
import com.autocall.app.util.DaysOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    scheduledCalls: List<ScheduledCall>,
    systemStatus: SystemStatus,
    onRefreshStatus: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (ScheduledCall) -> Unit,
    onDeleteClick: (ScheduledCall) -> Unit,
    onToggleEnabled: (ScheduledCall, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AutoCall") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add scheduled call")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PermissionBanner(
                    systemStatus = systemStatus,
                    onRefresh = onRefreshStatus,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (scheduledCalls.isEmpty()) {
                item {
                    Text(
                        text = "No scheduled calls yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(scheduledCalls, key = { it.id }) { call ->
                    SwipeToDeleteCallCard(
                        scheduledCall = call,
                        onEditClick = { onEditClick(call) },
                        onDeleteClick = { onDeleteClick(call) },
                        onToggleEnabled = { enabled -> onToggleEnabled(call, enabled) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteCallCard(
    scheduledCall: ScheduledCall,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDeleteClick()
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ScheduledCallCard(
            scheduledCall = scheduledCall,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
            onToggleEnabled = onToggleEnabled,
        )
    }
}

@Composable
private fun ScheduledCallCard(
    scheduledCall: ScheduledCall,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scheduledCall.contactName ?: "Unknown contact",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = scheduledCall.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${DaysOfWeek.formatShort(scheduledCall.days())} at " +
                            DayOfWeekFormatter.formatTime(scheduledCall.hour, scheduledCall.minute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (scheduledCall.isEnabled) {
                        val nextTrigger = AlarmScheduler.nextTriggerMillis(
                            scheduledCall.days(),
                            scheduledCall.hour,
                            scheduledCall.minute,
                        )
                        if (nextTrigger != null) {
                            Text(
                                text = "Next call: ${AlarmScheduler.formatTriggerTime(nextTrigger)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (scheduledCall.useSpeakerphone) {
                        Text(
                            text = "Speakerphone enabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Switch(
                    checked = scheduledCall.isEnabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
