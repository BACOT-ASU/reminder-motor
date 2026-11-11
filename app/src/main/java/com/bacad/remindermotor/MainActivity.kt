package com.bacad.remindermotor

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bacad.remindermotor.notify.Notifier
import com.bacad.remindermotor.notify.ReminderWorker
import com.bacad.remindermotor.ui.AddMotorScreen
import com.bacad.remindermotor.ui.MotorDetailScreen
import com.bacad.remindermotor.ui.MotorListScreen
import com.bacad.remindermotor.ui.MotorViewModel

class MainActivity : ComponentActivity() {

    /** Motor yang diminta dibuka dari notifikasi (0 = tidak ada). */
    private var openMotorRequest by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        ReminderWorker.schedule(this)
        readIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(
                        openMotorRequest = openMotorRequest,
                        onMotorRequestHandled = { openMotorRequest = 0L }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifier.EXTRA_MOTOR_ID, 0L) ?: 0L
        if (id > 0) openMotorRequest = id
    }
}

/**
 * Navigasi sederhana tanpa library tambahan:
 * -1 = daftar motor, 0 = tambah motor, >0 = detail motor dengan id tersebut.
 */
@Composable
fun App(
    openMotorRequest: Long,
    onMotorRequestHandled: () -> Unit,
    vm: MotorViewModel = viewModel()
) {
    var screen by rememberSaveable { mutableStateOf(-1L) }
    val motors by vm.motors.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Izin notifikasi (Android 13+), diminta sekali saat aplikasi dibuka.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* hasil tidak perlu diproses: worker mengecek izin sendiri */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Dibuka dari notifikasi: langsung ke motor yang bersangkutan.
    LaunchedEffect(openMotorRequest) {
        if (openMotorRequest > 0) {
            screen = openMotorRequest
            onMotorRequestHandled()
        }
    }

    BackHandler(enabled = screen != -1L) { screen = -1L }

    when {
        screen == -1L -> MotorListScreen(
            motors = motors,
            onAdd = { screen = 0L },
            onOpen = { screen = it.id },
            onCheckNow = {
                if (!Notifier.canNotify(context)) {
                    Toast.makeText(context, R.string.notif_permission_needed, Toast.LENGTH_LONG).show()
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    ReminderWorker.runNow(context, force = true)
                    Toast.makeText(context, R.string.check_now_started, Toast.LENGTH_SHORT).show()
                }
            }
        )
        screen == 0L -> AddMotorScreen(
            onBack = { screen = -1L },
            onSave = { name, type, radiator, km ->
                vm.addMotor(name, type, radiator, km) { newId -> screen = newId }
            }
        )
        else -> MotorDetailScreen(
            motorId = screen,
            vm = vm,
            onBack = { screen = -1L }
        )
    }
}
