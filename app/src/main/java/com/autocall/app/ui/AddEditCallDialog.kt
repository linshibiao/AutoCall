package com.autocall.app.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.autocall.app.data.ScheduledCall
import com.autocall.app.util.ContactPickerHelper
import com.autocall.app.util.DayOfWeekFormatter
import com.autocall.app.util.DaysOfWeek
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCallDialog(
    existingCall: ScheduledCall?,
    onDismiss: () -> Unit,
    onSave: (ScheduledCallForm) -> Unit,
) {
    val context = LocalContext.current
    val isEditing = existingCall != null
    val defaultDays = remember {
        setOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    }

    var contactName by remember(existingCall) {
        mutableStateOf(existingCall?.contactName.orEmpty())
    }
    var phoneNumber by remember(existingCall) {
        mutableStateOf(existingCall?.phoneNumber.orEmpty())
    }
    var selectedDays by remember(existingCall) {
        mutableStateOf(existingCall?.days()?.takeIf { it.isNotEmpty() } ?: defaultDays)
    }
    var hour by remember(existingCall) {
        mutableIntStateOf(existingCall?.hour ?: 9)
    }
    var minute by remember(existingCall) {
        mutableIntStateOf(existingCall?.minute ?: 0)
    }
    var useSpeakerphone by remember(existingCall) {
        mutableStateOf(existingCall?.useSpeakerphone ?: false)
    }
    var isEnabled by remember(existingCall) {
        mutableStateOf(existingCall?.isEnabled ?: true)
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val picked = ContactPickerHelper.resolveContact(context, uri) ?: return@rememberLauncherForActivityResult
        contactName = picked.name.orEmpty()
        phoneNumber = picked.phoneNumber
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit scheduled call" else "Add scheduled call") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { contactPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pick contact")
                }

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Contact name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone number") },
                    supportingText = {
                        Text("Use commas for pauses, e.g. 18005550199,,1,2")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text("Days of week")
                Text(
                    text = "Select one or more days",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DayOfWeekFormatter.allDays.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedDays.contains(value),
                            onClick = {
                                selectedDays = DaysOfWeek.toggle(selectedDays, value)
                            },
                            label = { Text(label.take(3)) },
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, selectedHour, selectedMinute ->
                                hour = selectedHour
                                minute = selectedMinute
                            },
                            hour,
                            minute,
                            false,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Time: ${DayOfWeekFormatter.formatTime(hour, minute)}")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useSpeakerphone,
                        onCheckedChange = { useSpeakerphone = it },
                    )
                    Text("Enable speakerphone on call")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                    )
                    Text("Schedule enabled")
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ScheduledCallForm(
                            id = existingCall?.id ?: 0L,
                            contactName = contactName,
                            phoneNumber = phoneNumber,
                            daysOfWeek = selectedDays,
                            hour = hour,
                            minute = minute,
                            isEnabled = isEnabled,
                            useSpeakerphone = useSpeakerphone,
                        ),
                    )
                },
                enabled = phoneNumber.isNotBlank() && selectedDays.isNotEmpty(),
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
