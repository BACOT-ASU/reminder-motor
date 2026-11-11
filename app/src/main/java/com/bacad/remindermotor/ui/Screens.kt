package com.bacad.remindermotor.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bacad.remindermotor.R
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
    onCheckNow: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onCheckNow) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = stringResource(R.string.check_now))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
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
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(motors, key = { it.id }) { motor ->
                    Card(
                        onClick = { onOpen(motor) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MotorPhoto(source = photoUri(motor.photoPath), size = 64.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(motor.name, style = MaterialTheme.typography.titleMedium)
                                val plate = motor.plateNumber
                                if (!plate.isNullOrBlank()) {
                                    Text(
                                        plate,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
}

@Composable
fun motorTypeLabel(type: MotorType): String = when (type) {
    MotorType.MATIC -> stringResource(R.string.type_matic)
    MotorType.MANUAL -> stringResource(R.string.type_manual)
}

/** Normalisasi nomor polisi: huruf besar, spasi rapi. Kosong = null. */
fun cleanPlate(text: String): String? =
    text.uppercase().trim().replace(Regex("\\s+"), " ").ifBlank { null }

// ------------------------------------------------------------
// Tambah motor
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMotorScreen(
    onBack: () -> Unit,
    onSave: (name: String, type: MotorType, hasRadiator: Boolean, odometerKm: Int, plate: String?, photo: Uri?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var type by remember { mutableStateOf(MotorType.MATIC) }
    var hasRadiator by remember { mutableStateOf(false) }
    var odometerText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val odometer = odometerText.toIntOrNull()
    val canSave = name.isNotBlank() && odometer != null && odometer >= 0 && !saving

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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhotoPickerField(current = photo, onPicked = { photo = it }, onRemove = { photo = null })

            NameAndPlateFields(name, { name = it }, plate, { plate = it })

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
                onClick = {
                    saving = true
                    onSave(name.trim(), type, hasRadiator, odometer ?: 0, cleanPlate(plate), photo)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

// ------------------------------------------------------------
// Edit motor: nama, nomor polisi, foto
// (jenis motor dan radiator tidak diubah karena menentukan daftar item)
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMotorScreen(
    motor: Motor,
    onBack: () -> Unit,
    onSave: (name: String, plate: String?, newPhoto: Uri?, removePhoto: Boolean) -> Unit
) {
    var name by remember(motor.id) { mutableStateOf(motor.name) }
    var plate by remember(motor.id) { mutableStateOf(motor.plateNumber ?: "") }
    var newPhoto by remember(motor.id) { mutableStateOf<Uri?>(null) }
    var removePhoto by remember(motor.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val shownPhoto = newPhoto ?: if (removePhoto) null else photoUri(motor.photoPath)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_motor)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhotoPickerField(
                current = shownPhoto,
                onPicked = { newPhoto = it; removePhoto = false },
                onRemove = { newPhoto = null; removePhoto = true }
            )
            NameAndPlateFields(name, { name = it }, plate, { plate = it })
            Text(
                "${motorTypeLabel(motor.type)} · ${stringResource(R.string.type_locked_note)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    saving = true
                    onSave(name.trim(), cleanPlate(plate), newPhoto, removePhoto)
                },
                enabled = name.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun NameAndPlateFields(
    name: String,
    onName: (String) -> Unit,
    plate: String,
    onPlate: (String) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = { onName(it.take(40)) },
        label = { Text(stringResource(R.string.motor_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = plate,
        onValueChange = { onPlate(it.uppercase().take(12)) },
        label = { Text(stringResource(R.string.plate_number)) },
        placeholder = { Text("B 1234 XYZ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth()
    )
}
