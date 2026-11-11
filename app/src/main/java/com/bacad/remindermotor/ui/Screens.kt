package com.bacad.remindermotor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.ItemType
import com.bacad.remindermotor.data.MaintenanceItem
import com.bacad.remindermotor.data.Motor
import com.bacad.remindermotor.data.MotorType

// ------------------------------------------------------------
// Daftar motor
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorListScreen(
    motors: List<Motor>,
    onAdd: () -> Unit,
    onOpen: (Motor) -> Unit,
    onCheckNow: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onCheckNow) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = stringResource(R.string.check_now)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_motor))
            }
        }
    ) { padding ->
        if (motors.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_motor_yet), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(motors, key = { it.id }) { motor ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onOpen(motor) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(motor.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${motorTypeLabel(motor.type)} · ${stringResource(R.string.km_value, motor.odometerKm)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun motorTypeLabel(type: MotorType): String = when (type) {
    MotorType.MATIC -> stringResource(R.string.type_matic)
    MotorType.MANUAL -> stringResource(R.string.type_manual)
}

// ------------------------------------------------------------
// Tambah motor
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMotorScreen(
    onBack: () -> Unit,
    onSave: (name: String, type: MotorType, hasRadiator: Boolean, odometerKm: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MotorType.MATIC) }
    var hasRadiator by remember { mutableStateOf(false) }
    var odometerText by remember { mutableStateOf("") }

    val odometer = odometerText.toIntOrNull()
    val canSave = name.isNotBlank() && odometer != null && odometer >= 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_motor)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.motor_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.motor_type), style = MaterialTheme.typography.titleSmall)
            MotorType.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = type == option, onClick = { type = option }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = type == option, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(motorTypeLabel(option))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.has_radiator), modifier = Modifier.weight(1f))
                Switch(checked = hasRadiator, onCheckedChange = { hasRadiator = it })
            }

            OutlinedTextField(
                value = odometerText,
                onValueChange = { new -> odometerText = new.filter { it.isDigit() }.take(7) },
                label = { Text(stringResource(R.string.current_odometer)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.start_today_note), style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSave(name.trim(), type, hasRadiator, odometer ?: 0) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
