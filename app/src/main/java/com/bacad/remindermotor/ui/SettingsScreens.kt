package com.bacad.remindermotor.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bacad.remindermotor.R
import com.bacad.remindermotor.notify.ReminderWorker
import com.bacad.remindermotor.settings.AppSettings
import java.time.LocalDate

// ------------------------------------------------------------
// Pilih bahasa saat pertama kali membuka aplikasi.
// Teks di layar ini sengaja dua bahasa karena bahasanya belum dipilih.
// ------------------------------------------------------------

@Composable
fun LanguagePickerScreen(onChosen: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Pilih bahasa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Choose language", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bisa diubah nanti di Pengaturan · You can change this later in Settings",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { AppSettings.setLanguage(context, AppSettings.LANG_ID); onChosen() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Bahasa Indonesia") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { AppSettings.setLanguage(context, AppSettings.LANG_EN); onChosen() },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("English") }
    }
}

// ------------------------------------------------------------
// Pengaturan umum: bahasa, jam notifikasi, backup & restore
// ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MotorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var hour by remember { mutableStateOf(AppSettings.notifHour(context)) }
    var minute by remember { mutableStateOf(AppSettings.notifMinute(context)) }
    val currentLang = AppSettings.currentLanguage()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) vm.exportBackup(uri) { ok ->
            Toast.makeText(context, if (ok) R.string.backup_done else R.string.backup_failed, Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestoreUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            // ---- Bahasa ----
            SectionTitle(stringResource(R.string.language))
            listOf(AppSettings.LANG_ID to "Bahasa Indonesia", AppSettings.LANG_EN to "English").forEach { (code, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currentLang == code,
                            onClick = { if (currentLang != code) AppSettings.setLanguage(context, code) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentLang == code, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---- Notifikasi ----
            SectionTitle(stringResource(R.string.notifications))
            SettingRow(
                title = stringResource(R.string.notif_time),
                value = "%02d:%02d".format(hour, minute),
                onClick = { showTimePicker = true }
            )
            Text(
                stringResource(R.string.battery_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---- Backup ----
            SectionTitle(stringResource(R.string.backup_restore))
            SettingRow(
                title = stringResource(R.string.backup_now),
                value = stringResource(R.string.backup_now_desc),
                onClick = { exportLauncher.launch("reminder-motor-${LocalDate.now()}.json") }
            )
            SettingRow(
                title = stringResource(R.string.restore),
                value = stringResource(R.string.restore_desc),
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.notif_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour
                    minute = state.minute
                    AppSettings.setNotifTime(context, state.hour, state.minute)
                    ReminderWorker.reschedule(context)
                    showTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.restore)) },
            text = { Text(stringResource(R.string.restore_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    vm.importBackup(uri) { ok ->
                        Toast.makeText(context, if (ok) R.string.restore_done else R.string.restore_failed, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
