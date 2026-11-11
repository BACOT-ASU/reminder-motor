package com.bacad.remindermotor.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.Motor
import com.bacad.remindermotor.data.TrackingMode
import com.bacad.remindermotor.data.Trip
import com.bacad.remindermotor.data.TripStatus
import com.bacad.remindermotor.settings.AppSettings
import com.bacad.remindermotor.tracking.Calibration
import com.bacad.remindermotor.tracking.TrackingController
import com.bacad.remindermotor.tracking.TrackingService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private fun km1(meters: Double) = String.format(Locale.getDefault(), "%.1f", meters / 1000.0)

@Composable
fun trackingModeLabel(mode: TrackingMode): String = stringResource(
    when (mode) {
        TrackingMode.OFF -> R.string.tracking_mode_off
        TrackingMode.MANUAL -> R.string.tracking_mode_manual
        TrackingMode.BLUETOOTH -> R.string.tracking_mode_bluetooth
        TrackingMode.AUTO -> R.string.tracking_mode_auto
    }
)

@Composable
private fun trackingModeDesc(mode: TrackingMode): String = stringResource(
    when (mode) {
        TrackingMode.OFF -> R.string.tracking_mode_off_desc
        TrackingMode.MANUAL -> R.string.tracking_mode_manual_desc
        TrackingMode.BLUETOOTH -> R.string.tracking_mode_bluetooth_desc
        TrackingMode.AUTO -> R.string.tracking_mode_auto_desc
    }
)

// ------------------------------------------------------------
// Bagian Pengaturan: pilih mode + daftar izin
// ------------------------------------------------------------

@Composable
fun TrackingSettingsSection(onOpenTrips: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppSettings.trackingMode(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val afterPermission: () -> Unit = {
        refresh++
        TrackingController.applyMode(context)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { afterPermission() }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { afterPermission() }

    TrackingModeRadios(mode) { new ->
        mode = new
        AppSettings.setTrackingMode(context, new)
        TrackingController.applyMode(context)
    }

    if (mode != TrackingMode.OFF) {
        // `refresh` dibaca supaya status izin dihitung ulang setiap kembali ke layar ini.
        @Suppress("UNUSED_VARIABLE") val r = refresh
        val needsBackground = mode != TrackingMode.MANUAL

        Text(
            stringResource(R.string.tracking_permissions),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        PermissionRow(stringResource(R.string.perm_location), TrackingController.hasLocation(context)) {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (needsBackground && Build.VERSION.SDK_INT >= 29) {
            PermissionRow(
                stringResource(R.string.perm_background_location),
                TrackingController.hasBackgroundLocation(context),
                enabled = TrackingController.hasLocation(context)
            ) { singleLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        }
        if (mode == TrackingMode.BLUETOOTH && Build.VERSION.SDK_INT >= 31) {
            PermissionRow(stringResource(R.string.perm_bluetooth), TrackingController.hasBluetoothPermission(context)) {
                singleLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (mode == TrackingMode.AUTO && Build.VERSION.SDK_INT >= 29) {
            PermissionRow(stringResource(R.string.perm_activity), TrackingController.hasActivityPermission(context)) {
                singleLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (needsBackground) {
            PermissionRow(stringResource(R.string.perm_battery), TrackingController.isBatteryUnrestricted(context)) {
                openBatterySettings(context)
            }
        }
        if (mode == TrackingMode.BLUETOOTH) {
            Text(
                stringResource(R.string.bluetooth_setup_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTrips).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.trip_history), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.trip_history_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrackingModeRadios(selected: TrackingMode, onSelect: (TrackingMode) -> Unit) {
    TrackingMode.entries.forEach { option ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected == option, onClick = { onSelect(option) }, role = Role.RadioButton)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected == option, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(trackingModeLabel(option))
                Text(trackingModeDesc(option), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, enabled: Boolean = true, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!granted) {
            TextButton(onClick = onRequest, enabled = enabled) { Text(stringResource(R.string.perm_allow)) }
        }
    }
}

@SuppressLint("BatteryLife")
private fun openBatterySettings(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(direct)
    } catch (e: Exception) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

// ------------------------------------------------------------
// Kartu di layar motor: mulai / selesai perjalanan manual
// ------------------------------------------------------------

@Composable
fun TrackingCard(motor: Motor) {
    val context = LocalContext.current
    val mode = remember { AppSettings.trackingMode(context) }
    if (mode == TrackingMode.OFF) return

    val state by TrackingService.state.collectAsState()
    var needPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            TrackingService.start(context, TrackingMode.MANUAL, motor.id)
        } else needPermission = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Navigation, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (state.running) {
                    Text(stringResource(R.string.tracking_recording), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.tracking_distance, km1(state.distanceMeters)),
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.tracking_gps), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.tracking_calibration, Calibration.factorPercent(context, motor.id)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (state.running) {
                Button(onClick = { TrackingService.stop(context) }) { Text(stringResource(R.string.tracking_stop)) }
            } else {
                Button(onClick = {
                    if (TrackingController.hasLocation(context)) {
                        TrackingService.start(context, TrackingMode.MANUAL, motor.id)
                    } else {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }) { Text(stringResource(R.string.tracking_start)) }
            }
        }
        if (needPermission) {
            Text(
                stringResource(R.string.perm_location_denied),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            )
        }
    }
}

// ------------------------------------------------------------
// Riwayat & konfirmasi perjalanan
// ------------------------------------------------------------

private val tripTimeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(vm: MotorViewModel, onBack: () -> Unit) {
    val trips by vm.trips.collectAsState(initial = emptyList())
    val motors by vm.motors.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (trips.isEmpty()) {
            Text(
                stringResource(R.string.trip_empty),
                modifier = Modifier.padding(padding).padding(24.dp)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
        ) {
            items(trips, key = { it.id }) { trip ->
                TripCard(trip, motors, onConfirm = { motorId -> vm.confirmTrip(trip.id, motorId) })
            }
        }
    }
}

@Composable
private fun TripCard(trip: Trip, motors: List<Motor>, onConfirm: (Long?) -> Unit) {
    val time = Instant.ofEpochMilli(trip.startTime).atZone(ZoneId.systemDefault()).format(tripTimeFormat)
    val motorName = motors.firstOrNull { it.id == trip.motorId }?.let { it.plateNumber ?: it.name }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${km1(trip.distanceMeters)} km · $time", fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.trip_details, trip.avgSpeedKmh.toInt(), trip.maxSpeedKmh.toInt(), trackingModeLabel(trip.mode)),
                style = MaterialTheme.typography.bodySmall
            )
            when (trip.status) {
                TripStatus.CONFIRMED -> Text(
                    stringResource(R.string.trip_status_confirmed, motorName ?: "?"),
                    color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium
                )
                TripStatus.NOT_MOTOR -> Text(
                    stringResource(R.string.trip_status_not_motor),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                )
                TripStatus.PENDING -> {
                    Text(stringResource(R.string.trip_confirm_text), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        motors.forEach { m ->
                            AssistChip(
                                onClick = { onConfirm(m.id) },
                                label = { Text(m.plateNumber?.takeIf { it.isNotBlank() } ?: m.name) }
                            )
                        }
                    }
                    OutlinedButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.trip_not_motor)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------
// Pilih perangkat Bluetooth pemicu (di layar edit motor)
// ------------------------------------------------------------

private data class BtDevice(val name: String, val address: String)

@SuppressLint("MissingPermission")
private fun bondedDevices(context: Context): List<BtDevice> = try {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    adapter?.bondedDevices.orEmpty().map { BtDevice(it.name ?: it.address, it.address) }.sortedBy { it.name }
} catch (e: SecurityException) {
    emptyList()
}

@Composable
fun BluetoothTriggerField(current: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<BtDevice>>(emptyList()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { devices = bondedDevices(context); open = true }
    }

    val currentName = remember(current, open) {
        current?.let { addr -> bondedDevices(context).firstOrNull { it.address == addr }?.name ?: addr }
    }

    OutlinedButton(
        onClick = {
            if (TrackingController.hasBluetoothPermission(context)) {
                devices = bondedDevices(context); open = true
            } else if (Build.VERSION.SDK_INT >= 31) {
                permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.Bluetooth, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            if (currentName != null) stringResource(R.string.bt_trigger_set, currentName)
            else stringResource(R.string.bt_trigger_none)
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.bt_trigger_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.bt_trigger_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    if (devices.isEmpty()) Text(stringResource(R.string.bt_no_devices))
                    devices.forEach { d ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = d.address == current, onClick = { onChange(d.address); open = false })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = d.address == current, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(d.name)
                        }
                    }
                    HorizontalDivider()
                }
            },
            confirmButton = {
                TextButton(onClick = { onChange(null); open = false }) { Text(stringResource(R.string.bt_trigger_clear)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
