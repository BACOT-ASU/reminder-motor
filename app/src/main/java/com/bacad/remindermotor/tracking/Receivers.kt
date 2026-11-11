package com.bacad.remindermotor.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bacad.remindermotor.data.AppDatabase
import com.bacad.remindermotor.data.TrackingMode
import com.bacad.remindermotor.notify.ReminderWorker
import com.bacad.remindermotor.settings.AppSettings
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Jalankan pekerjaan singkat di latar belakang dari dalam receiver. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } finally { pending.finish() }
    }
}

/** Tombol pada notifikasi konfirmasi perjalanan. motorId 0 = "Bukan motor". */
class TripActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getLongExtra(EXTRA_TRIP_ID, 0L)
        val motorId = intent.getLongExtra(EXTRA_MOTOR_ID, 0L).takeIf { it > 0 }
        if (tripId <= 0) return
        runAsync {
            TripRepository(context.applicationContext).confirm(tripId, motorId)
            TripNotifications.cancel(context, tripId)
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "tripId"
        const val EXTRA_MOTOR_ID = "motorId"
    }
}

/** Mode Bluetooth: mulai merekam saat HP tersambung ke perangkat pemicu, berhenti saat terputus. */
class BluetoothTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppSettings.trackingMode(context) != TrackingMode.BLUETOOTH) return
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val address = try { device?.address } catch (e: SecurityException) { null } ?: return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> runAsync {
                val motors = AppDatabase.get(context).dao().getMotorsByBluetooth(address)
                if (motors.isNotEmpty()) {
                    // Satu perangkat dipakai beberapa motor (mis. TWS yang sama): biarkan pengguna memilih nanti.
                    val suggested = motors.singleOrNull()?.id
                    if (!TrackingService.start(context.applicationContext, TrackingMode.BLUETOOTH, suggested)) {
                        TrackingNotices.showStartBlocked(context.applicationContext)
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val s = TrackingService.state.value
                if (s.running && s.mode == TrackingMode.BLUETOOTH) TrackingService.stop(context)
            }
        }
    }
}

/** Mode deteksi otomatis: Google Activity Recognition memberi tahu saat mulai/berhenti di kendaraan. */
class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (AppSettings.trackingMode(context) != TrackingMode.AUTO) return
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val last = result.transitionEvents.lastOrNull() ?: return
        val isVehicle = last.activityType == DetectedActivity.IN_VEHICLE ||
            last.activityType == DetectedActivity.ON_BICYCLE

        if (!isVehicle) return
        if (last.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
            if (!TrackingService.start(context.applicationContext, TrackingMode.AUTO, null)) {
                TrackingNotices.showStartBlocked(context.applicationContext)
            }
        } else {
            val s = TrackingService.state.value
            if (s.running && s.mode == TrackingMode.AUTO) TrackingService.stop(context)
        }
    }
}

/** Setelah HP dinyalakan ulang: pasang lagi jadwal cek harian dan deteksi otomatis. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderWorker.schedule(context)
        TrackingController.applyMode(context)
    }
}
