package com.autocall.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.autocall.app.util.PermissionHelper

@Composable
fun PermissionBanner(
    systemStatus: SystemStatus,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!systemStatus.needsAttention) return

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onRefresh() }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Action required for reliable auto-calls",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (!systemStatus.hasAllRuntimePermissions) {
                Text(
                    text = "Grant phone, contacts, and notification permissions so scheduled calls can run.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(PermissionHelper.allRuntimePermissions())
                    },
                ) {
                    Text("Grant permissions")
                }
            }

            if (!systemStatus.canScheduleExactAlarms) {
                Text(
                    text = "Allow exact alarms so calls fire on time, even in Doze mode.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(PermissionHelper.createExactAlarmSettingsIntent(context))
                    },
                ) {
                    Text("Allow exact alarms")
                }
            }

            if (!systemStatus.isIgnoringBatteryOptimizations) {
                Text(
                    text = "Disable battery optimization to prevent missed scheduled calls.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(PermissionHelper.createBatteryOptimizationIntent(context))
                    },
                ) {
                    Text("Disable battery optimization")
                }
            }

            OutlinedButton(onClick = onRefresh) {
                Text("Refresh status")
            }
        }
    }
}
