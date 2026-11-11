package com.bacad.remindermotor.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.ItemState
import com.bacad.remindermotor.data.ItemStatus
import com.bacad.remindermotor.data.ItemType
import com.bacad.remindermotor.data.Motor
import com.bacad.remindermotor.data.ServiceAction
import com.bacad.remindermotor.data.StatusCalc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ------------------------------------------------------------
// Warna status (warna bukan satu-satunya penanda: selalu ada teks juga)
// ------------------------------------------------------------

private data class StatusColors(val container: Color, val content: Color)

private fun colorsFor(status: ItemStatus) = when (status) {
    ItemStatus.GREEN -> StatusColors(Color(0xFFE8F5E9), Color(0xFF2E7D32))
    ItemStatus.YELLOW -> StatusColors(Color(0xFFFFF8E1), Color(0xFFB26A00))
    ItemStatus.RED -> StatusColors(Color(0xFFFFEBEE), Color(0xFFC62828))
}

@Composable
private fun statusLabel(state: ItemState): String = when (state.status) {
    ItemStatus.GREEN -> stringResource(R.string.status_ok)
    ItemStatus.YELLOW -> stringResource(R.string.status_soon)
    ItemStatus.RED -> if (state.item.type == ItemType.CHECK)
        stringResource(R.string.status_check_now) else stringResource(R.string.status_replace_now)
}

@Composable
private fun remainingKmText(km: Int): String =
    if (km > 0) stringResource(R.string.remaining_km, km) else stringResource(R.string.overdue_km, -km)

@Composable
private fun remainingDaysText(days: Int): String =
    if (days > 0) stringResource(R.string.remaining_days, days) else stringResource(R.string.overdue_days, -days)

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFormat)

// ------------------------------------------------------------
// Layar utama motor: header odometer + grid ikon
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotorDetailScreen(
    motorId: Long,
    vm: MotorViewModel,
    onBack: () -> Unit,
    onManageItems: () -> Unit
) {
    val motor by remember(motorId) { vm.repo.observeMotor(motorId) }.collectAsState(initial = null)
    val items by remember(motorId) { vm.repo.observeItems(motorId) }.collectAsState(initial = emptyList())
    val records by remember(motorId) { vm.repo.observeLatestRecords(motorId) }.collectAsState(initial = emptyList())

    var confirmDelete by remember { mutableStateOf(false) }
    var editOdometer by remember { mutableStateOf(false) }
    var selectedItemId by remember { mutableStateOf<Long?>(null) }

    val m = motor
    val today = LocalDate.now().toEpochDay()
    val states: List<ItemState> = if (m == null) emptyList() else {
        val lastByItem = records.associateBy { it.itemId }
        items.filter { it.active }.map { StatusCalc.compute(it, lastByItem[it.id], m.odometerKm, today) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(m?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onManageItems) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.manage_items))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_motor))
                    }
                }
            )
        }
    ) { padding ->
        if (m == null) return@Scaffold
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OdometerHeader(m, today, states, onEdit = { editOdometer = true })
            }
            items(states, key = { it.item.id }) { state ->
                ItemTile(state, onClick = { selectedItemId = state.item.id })
            }
        }
    }

    // ---- Dialog-dialog ----
    if (m != null && editOdometer) {
        OdometerDialog(
            currentKm = m.odometerKm,
            onDismiss = { editOdometer = false },
            onSave = { km ->
                vm.updateOdometer(m.id, km)
                editOdometer = false
            }
        )
    }

    val selected = states.firstOrNull { it.item.id == selectedItemId }
    if (m != null && selected != null) {
        ItemDetailDialog(
            state = selected,
            motor = m,
            onDismiss = { selectedItemId = null },
            onMarkDone = {
                vm.markDone(selected.item, m.odometerKm)
                selectedItemId = null
            },
            onSetLast = { date, km ->
                vm.setLastService(selected.item, date, km)
                selectedItemId = null
            }
        )
    }

    if (m != null && confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_motor)) },
            text = { Text(stringResource(R.string.delete_motor_confirm, m.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMotor(m)
                    confirmDelete = false
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun OdometerHeader(motor: Motor, today: Long, states: List<ItemState>, onEdit: () -> Unit) {
    val reds = states.count { it.status == ItemStatus.RED }
    val yellows = states.count { it.status == ItemStatus.YELLOW }
    val daysAgo = (today - motor.odometerDate).toInt()

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.km_value, motor.odometerKm),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${motorTypeLabel(motor.type)} · " + if (daysAgo <= 0)
                        stringResource(R.string.odometer_updated_today)
                    else stringResource(R.string.odometer_updated_days_ago, daysAgo),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.update_km)) }
        }
        if (reds > 0 || yellows > 0) {
            HorizontalDivider()
            Text(
                stringResource(R.string.summary_counts, reds, yellows),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ItemTile(state: ItemState, onClick: () -> Unit) {
    val c = colorsFor(state.status)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = c.container)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(52.dp).background(c.content, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(itemIcon(state.item.key), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                itemDisplayName(state.item),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                statusLabel(state),
                style = MaterialTheme.typography.labelMedium,
                color = c.content,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            state.remainingKm?.let {
                Text(remainingKmText(it), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            state.remainingDays?.let {
                Text(remainingDaysText(it), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

// ------------------------------------------------------------
// Dialog update odometer
// ------------------------------------------------------------

@Composable
private fun OdometerDialog(currentKm: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(currentKm.toString()) }
    val km = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_km)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(7) },
                    label = { Text(stringResource(R.string.current_odometer)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (km != null && km < currentKm) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.odometer_lower_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { km?.let(onSave) }, enabled = km != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ------------------------------------------------------------
// Dialog detail item
// ------------------------------------------------------------

@Composable
private fun ItemDetailDialog(
    state: ItemState,
    motor: Motor,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit,
    onSetLast: (date: Long, km: Int) -> Unit
) {
    var settingLast by remember { mutableStateOf(false) }
    if (settingLast) {
        SetLastServiceDialog(
            state = state,
            motor = motor,
            onDismiss = { settingLast = false },
            onSave = onSetLast
        )
        return
    }

    val item = state.item
    val c = colorsFor(state.status)
    val isCheck = item.type == ItemType.CHECK
    val last = state.lastRecord

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(itemIcon(item.key), contentDescription = null, tint = c.content) },
        title = { Text(itemDisplayName(item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(statusLabel(state), color = c.content, fontWeight = FontWeight.Bold)

                val intervalParts = buildList {
                    item.intervalKm?.let { add(stringResource(R.string.every_km, it)) }
                    item.intervalDays?.let { add(stringResource(R.string.every_days, it)) }
                }
                Text(stringResource(R.string.interval_label, intervalParts.joinToString(" / ")))

                if (last != null) {
                    val label = if (last.action == ServiceAction.INITIAL)
                        stringResource(R.string.counting_since, formatDate(last.date), last.km)
                    else stringResource(R.string.last_done, formatDate(last.date), last.km)
                    Text(label)
                }
                state.remainingKm?.let { Text(remainingKmText(it)) }
                state.remainingDays?.let { Text(remainingDaysText(it)) }

                if (last?.action == ServiceAction.INITIAL) {
                    Text(
                        stringResource(R.string.initial_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = onMarkDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isCheck) R.string.mark_checked else R.string.mark_replaced))
                }
                Text(
                    stringResource(R.string.mark_done_note, motor.odometerKm),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { settingLast = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_last_service))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

// ------------------------------------------------------------
// Dialog atur servis terakhir (tanggal + km di masa lalu)
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetLastServiceDialog(
    state: ItemState,
    motor: Motor,
    onDismiss: () -> Unit,
    onSave: (date: Long, km: Int) -> Unit
) {
    val today = LocalDate.now().toEpochDay()
    val needsKm = state.item.intervalKm != null

    var date by remember { mutableStateOf(state.lastRecord?.date ?: today) }
    var kmText by remember { mutableStateOf((state.lastRecord?.km ?: motor.odometerKm).toString()) }
    var showPicker by remember { mutableStateOf(false) }

    val km = if (needsKm) kmText.toIntOrNull() else motor.odometerKm
    val dateError = date > today
    val kmError = km != null && km > motor.odometerKm
    val canSave = km != null && !dateError && !kmError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_last_service)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(itemDisplayName(state.item), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDate(date))
                }
                if (dateError) {
                    Text(stringResource(R.string.date_future_error), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (needsKm) {
                    OutlinedTextField(
                        value = kmText,
                        onValueChange = { new -> kmText = new.filter { it.isDigit() }.take(7) },
                        label = { Text(stringResource(R.string.km_at_service)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    if (kmError) {
                        Text(stringResource(R.string.km_above_odometer_error, motor.odometerKm),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (km != null) onSave(date, km) }, enabled = canSave) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
