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
    onSave: (maxRetries: Int, retryDeadlineMinutes: Int) -> Unit,
) {
    var retriesText by remember(retrySettings) {
        mutableStateOf(retrySettings.maxRetries.toString())
    }
    var deadlineText by remember(retrySettings) {
        mutableStateOf(retrySettings.retryDeadlineMinutes.toString())
    }

    val retries = retriesText.toIntOrNull()
    val deadlineMinutes = deadlineText.toIntOrNull()
    val canSave = retries != null && deadlineMinutes != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retry settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "These settings apply when a scheduled call has an expected duration.",
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (retries != null && deadlineMinutes != null) {
                        onSave(retries, deadlineMinutes)
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
