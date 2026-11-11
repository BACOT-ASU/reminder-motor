package com.bacad.remindermotor

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bacad.remindermotor.notify.Notifier
import com.bacad.remindermotor.notify.ReminderWorker
import com.bacad.remindermotor.settings.AppSettings
import com.bacad.remindermotor.ui.AddMotorScreen
import com.bacad.remindermotor.ui.EditMotorScreen
import com.bacad.remindermotor.ui.ItemSettingsScreen
import com.bacad.remindermotor.ui.LanguagePickerScreen
import com.bacad.remindermotor.ui.MotorDetailScreen
import com.bacad.remindermotor.ui.MotorListScreen
import com.bacad.remindermotor.ui.MotorViewModel
import com.bacad.remindermotor.ui.SettingsScreen

/** AppCompatActivity dipakai supaya bahasa aplikasi bisa diganti dari dalam aplikasi. */
class MainActivity : AppCompatActivity() {

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
                    var languageChosen by remember { mutableStateOf(AppSettings.isLanguageChosen(this)) }
                    if (!languageChosen) {
                        LanguagePickerScreen(onChosen = { languageChosen = true })
                    } else {
                        App(
                            openMotorRequest = openMotorRequest,
                            onMotorRequestHandled = { openMotorRequest = 0L }
                        )
                    }
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

private object Route {
    const val LIST = "list"
    const val ADD = "add"
    const val DETAIL = "detail"
    const val ITEMS = "items"
    const val SETTINGS = "settings"
    const val EDIT = "edit"
}

@Composable
fun App(
    openMotorRequest: Long,
    onMotorRequestHandled: () -> Unit,
    vm: MotorViewModel = viewModel()
) {
    var route by rememberSaveable { mutableStateOf(Route.LIST) }
    var motorId by rememberSaveable { mutableLongStateOf(0L) }
    val motors by vm.motors.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Izin notifikasi (Android 13+), diminta sekali saat aplikasi dibuka.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* worker mengecek izin sendiri */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Dibuka dari notifikasi: langsung ke motor yang bersangkutan.
    LaunchedEffect(openMotorRequest) {
        if (openMotorRequest > 0) {
            motorId = openMotorRequest
            route = Route.DETAIL
            onMotorRequestHandled()
        }
    }

    BackHandler(enabled = route != Route.LIST) {
        route = if (route == Route.ITEMS || route == Route.EDIT) Route.DETAIL else Route.LIST
    }

    when (route) {
        Route.ADD -> AddMotorScreen(
            onBack = { route = Route.LIST },
            onSave = { name, type, radiator, km, plate, photo ->
                vm.addMotor(name, type, radiator, km, plate, photo) { newId ->
                    motorId = newId
                    route = Route.DETAIL
                }
            }
        )
        Route.DETAIL -> MotorDetailScreen(
            motorId = motorId,
            vm = vm,
            onBack = { route = Route.LIST },
            onManageItems = { route = Route.ITEMS },
            onEdit = { route = Route.EDIT }
        )
        Route.EDIT -> {
            val motor = motors.firstOrNull { it.id == motorId }
            if (motor != null) {
                EditMotorScreen(
                    motor = motor,
                    onBack = { route = Route.DETAIL },
                    onSave = { name, plate, newPhoto, removePhoto ->
                        vm.updateMotorInfo(motor, name, plate, newPhoto, removePhoto) { route = Route.DETAIL }
                    }
                )
            }
        }
        Route.ITEMS -> ItemSettingsScreen(
            motorId = motorId,
            vm = vm,
            onBack = { route = Route.DETAIL }
        )
        Route.SETTINGS -> SettingsScreen(
            vm = vm,
            onBack = { route = Route.LIST }
        )
        else -> MotorListScreen(
            motors = motors,
            onAdd = { route = Route.ADD },
            onOpen = {
                motorId = it.id
                route = Route.DETAIL
            },
            onCheckNow = {
                if (!Notifier.canNotify(context)) {
                    Toast.makeText(context, R.string.notif_permission_needed, Toast.LENGTH_LONG).show()
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    ReminderWorker.runNow(context, force = true)
                    Toast.makeText(context, R.string.check_now_started, Toast.LENGTH_SHORT).show()
                }
            },
            onSettings = { route = Route.SETTINGS }
        )
    }
}
