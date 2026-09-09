package com.autocall.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autocall.app.data.RetrySettings

@Composable
fun SettingsDialog(
    retrySettings: RetrySettings,
    onDismiss: () -> Unit,
    onSave: (
        maxRetries: Int,
        retryDeadlineMinutes: Int,
        busyDeferPromptMinutes: Int,
    ) -> Unit,
) {
    var retriesText by remember(retrySettings) {
        mutableStateOf(retrySettings.maxRetries.toString())
    }
    var deadlineText by remember(retrySettings) {
        mutableStateOf(retrySettings.retryDeadlineMinutes.toString())
    }
    var busyDeferText by remember(retrySettings) {
        mutableStateOf(retrySettings.busyDeferPromptMinutes.toString())
    }

    val retries = retriesText.toIntOrNull()
    val deadlineMinutes = deadlineText.toIntOrNull()
    val busyDeferMinutes = busyDeferText.toIntOrNull()
    val canSave = retries != null && deadlineMinutes != null && busyDeferMinutes != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Call settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "These settings control redials and what happens when a scheduled call is delayed by another call.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = retriesText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit) && value.length <= 2) {
                            retriesText = value
                        }
                    },
                    label = { Text("Max retries") },
                    supportingText = {
                        Text(
                            "How many times to redial after a duration mismatch. Default is ${RetrySettings.DEFAULT_MAX_RETRIES}.",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit) && value.length <= 3) {
                            deadlineText = value
                        }
                    },
                    label = { Text("Retry deadline (minutes)") },
                    supportingText = {
                        Text(
                            "Don't retry if more than this many minutes have passed since the original scheduled time. Default is ${RetrySettings.DEFAULT_RETRY_DEADLINE_MINUTES}.",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = busyDeferText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit) && value.length <= 3) {
                            busyDeferText = value
                        }
                    },
                    label = { Text("Busy delay prompt (minutes)") },
                    supportingText = {
                        Text(
                            "If a scheduled call is delayed because another call is in progress, ask whether to place or skip after this many minutes. Default is ${RetrySettings.DEFAULT_BUSY_DEFER_PROMPT_MINUTES}.",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (retries != null && deadlineMinutes != null && busyDeferMinutes != null) {
                        onSave(retries, deadlineMinutes, busyDeferMinutes)
                    }
                },
                enabled = canSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
