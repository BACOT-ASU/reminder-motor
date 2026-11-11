package com.bacad.remindermotor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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

/**
 * Atur item per motor: nyalakan/matikan, ubah interval, tambah dan hapus item kustom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSettingsScreen(motorId: Long, vm: MotorViewModel, onBack: () -> Unit) {
    val motor by remember(motorId) { vm.repo.observeMotor(motorId) }.collectAsState(initial = null)
    val items by remember(motorId) { vm.repo.observeItems(motorId) }.collectAsState(initial = emptyList())

    var editing by remember { mutableStateOf<MaintenanceItem?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_items)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_custom_item)) }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.manual_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = item }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(itemIcon(item.key), contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(itemDisplayName(item), style = MaterialTheme.typography.bodyLarge)
                        Text(intervalText(item), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = item.active,
                        onCheckedChange = { vm.updateItem(item.copy(active = it)) }
                    )
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.padding(40.dp)) }
        }
    }

    editing?.let { item ->
        ItemEditDialog(
            initial = item,
            onDismiss = { editing = null },
            onSave = { name, type, km, days ->
                vm.updateItem(
                    item.copy(
                        customName = if (item.key == null) name else item.customName,
                        type = type,
                        intervalKm = km,
                        intervalDays = days
                    )
                )
                editing = null
            },
            onDelete = if (item.key == null) {
                { vm.deleteItem(item); editing = null }
            } else null
        )
    }

    val m = motor
    if (adding && m != null) {
        ItemEditDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { name, type, km, days ->
                vm.addCustomItem(m, name, type, km, days)
                adding = false
            },
            onDelete = null
        )
    }
}

@Composable
private fun intervalText(item: MaintenanceItem): String {
    val parts = buildList {
        item.intervalKm?.let { add(stringResource(R.string.every_km, it)) }
        item.intervalDays?.let { add(stringResource(R.string.every_days, it)) }
    }
    val type = stringResource(if (item.type == ItemType.CHECK) R.string.item_type_check else R.string.item_type_replace)
    return "$type · ${parts.joinToString(" / ")}"
}

/**
 * Dialog ubah item (initial != null) atau tambah item kustom (initial == null).
 * Nama hanya bisa diubah untuk item kustom.
 */
@Composable
private fun ItemEditDialog(
    initial: MaintenanceItem?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: ItemType, km: Int?, days: Int?) -> Unit,
    onDelete: (() -> Unit)?
) {
    val isCustom = initial == null || initial.key == null
    var name by remember { mutableStateOf(initial?.customName ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ItemType.REPLACE) }
    var kmText by remember { mutableStateOf(initial?.intervalKm?.toString() ?: "") }
    var daysText by remember { mutableStateOf(initial?.intervalDays?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val km = kmText.toIntOrNull()?.takeIf { it > 0 }
    val days = daysText.toIntOrNull()?.takeIf { it > 0 }
    val hasInterval = km != null || days != null
    val canSave = hasInterval && (!isCustom || name.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) stringResource(R.string.add_custom_item)
                else itemDisplayName(initial)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCustom) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text(stringResource(R.string.item_name)) },
                        singleLine = true
                    )
                }
                ItemType.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = type == option, onClick = { type = option }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = type == option, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (option == ItemType.CHECK) R.string.item_type_check else R.string.item_type_replace
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { new -> kmText = new.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.interval_km_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { new -> daysText = new.filter { it.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.interval_days_field)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    stringResource(R.string.interval_fields_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasInterval) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete_item), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), type, km, days) }, enabled = canSave) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_item)) },
            text = { Text(stringResource(R.string.delete_item_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
