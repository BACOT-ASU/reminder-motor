package com.bacad.remindermotor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bacad.remindermotor.data.AppDatabase
import com.bacad.remindermotor.data.Motor
import com.bacad.remindermotor.data.MotorRepository
import com.bacad.remindermotor.data.MotorType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MotorViewModel(app: Application) : AndroidViewModel(app) {
    val repo = MotorRepository(AppDatabase.get(app))

    val motors: StateFlow<List<Motor>> = repo.observeMotors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMotor(name: String, type: MotorType, hasRadiator: Boolean, odometerKm: Int, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.addMotor(name, type, hasRadiator, odometerKm)
            onDone(id)
        }
    }

    fun deleteMotor(motor: Motor) {
        viewModelScope.launch { repo.deleteMotor(motor) }
    }
}
