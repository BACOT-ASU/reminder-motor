package com.bacad.remindermotor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bacad.remindermotor.ui.AddMotorScreen
import com.bacad.remindermotor.ui.MotorDetailScreen
import com.bacad.remindermotor.ui.MotorListScreen
import com.bacad.remindermotor.ui.MotorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

/**
 * Navigasi sederhana tanpa library tambahan:
 * -1 = daftar motor, 0 = tambah motor, >0 = detail motor dengan id tersebut.
 */
@Composable
fun App(vm: MotorViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(-1L) }
    val motors by vm.motors.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != -1L) { screen = -1L }

    when {
        screen == -1L -> MotorListScreen(
            motors = motors,
            onAdd = { screen = 0L },
            onOpen = { screen = it.id }
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
