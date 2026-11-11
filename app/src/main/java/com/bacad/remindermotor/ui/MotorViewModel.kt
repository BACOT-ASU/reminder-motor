package com.bacad.remindermotor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bacad.remindermotor.data.AppDatabase
import com.bacad.remindermotor.data.Backup
import com.bacad.remindermotor.data.ItemType
import com.bacad.remindermotor.data.MaintenanceItem
import com.bacad.remindermotor.data.Motor
import com.bacad.remindermotor.data.MotorRepository
import com.bacad.remindermotor.data.MotorType
import com.bacad.remindermotor.data.PhotoStore
import com.bacad.remindermotor.tracking.TripNotifications
import com.bacad.remindermotor.tracking.TripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MotorViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val repo = MotorRepository(db, app)

    private val tripRepo = TripRepository(app)
    val trips = tripRepo.observeTrips()
    val pendingTripCount: StateFlow<Int> = tripRepo.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun confirmTrip(tripId: Long, motorId: Long?) {
        viewModelScope.launch {
            tripRepo.confirm(tripId, motorId)
            TripNotifications.cancel(getApplication(), tripId)
        }
    }

    val motors: StateFlow<List<Motor>> = repo.observeMotors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMotor(
        name: String,
        type: MotorType,
        hasRadiator: Boolean,
        odometerKm: Int,
        plateNumber: String?,
        photoUri: Uri?,
        onDone: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val photoPath = photoUri?.let { savePhoto(it) }
            val id = repo.addMotor(name, type, hasRadiator, odometerKm, plateNumber, photoPath)
            onDone(id)
        }
    }

    /**
     * Ubah nama/plat/foto. newPhotoUri != null = ganti foto; removePhoto = hapus foto.
     */
    fun updateMotorInfo(
        motor: Motor,
        name: String,
        plateNumber: String?,
        newPhotoUri: Uri?,
        removePhoto: Boolean,
        bluetoothTrigger: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            var photoPath = motor.photoPath
            if (newPhotoUri != null) {
                val saved = savePhoto(newPhotoUri)
                if (saved != null) {
                    PhotoStore.delete(motor.photoPath)
                    photoPath = saved
                }
            } else if (removePhoto) {
                PhotoStore.delete(motor.photoPath)
                photoPath = null
            }
            repo.updateMotor(
                motor.copy(name = name, plateNumber = plateNumber, photoPath = photoPath, bluetoothTrigger = bluetoothTrigger)
            )
            onDone()
        }
    }

    private suspend fun savePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        PhotoStore.saveFromUri(getApplication(), uri)
    }

    fun deleteMotor(motor: Motor) {
        viewModelScope.launch {
            repo.deleteMotor(motor)
            PhotoStore.delete(motor.photoPath)
        }
    }

    fun updateOdometer(motorId: Long, km: Int) {
        viewModelScope.launch { repo.updateOdometer(motorId, km) }
    }

    fun markDone(item: MaintenanceItem, odometerKm: Int) {
        viewModelScope.launch { repo.markDone(item, odometerKm) }
    }

    fun updateItem(item: MaintenanceItem) {
        viewModelScope.launch { repo.updateItem(item) }
    }

    fun deleteItem(item: MaintenanceItem) {
        viewModelScope.launch { repo.deleteItem(item) }
    }

    fun addCustomItem(motor: Motor, name: String, type: ItemType, km: Int?, days: Int?) {
        viewModelScope.launch { repo.addCustomItem(motor, name, type, km, days) }
    }

    /** Tulis backup ke [uri]. onResult(true) jika berhasil. */
    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = Backup.export(db, getApplication())
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            onResult(ok)
        }
    }

    /** Baca backup dari [uri] dan ganti semua data. onResult(true) jika berhasil. */
    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                }
                Backup.import(db, getApplication(), json)
            }.isSuccess
            onResult(ok)
        }
    }

    fun setLastService(item: MaintenanceItem, date: Long, km: Int) {
        viewModelScope.launch { repo.setLastService(item, date, km) }
    }
}
