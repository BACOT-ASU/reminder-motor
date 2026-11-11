package com.bacad.remindermotor.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bacad.remindermotor.MainActivity
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.TrackingMode
import com.bacad.remindermotor.notify.Notifier
import com.bacad.remindermotor.settings.AppSettings
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/** Mengatur pendaftaran pemicu sesuai mode, dan mengecek izin yang dibutuhkan. */
object TrackingController {

    /** Pasang/lepas deteksi otomatis sesuai mode yang dipilih. Aman dipanggil berkali-kali. */
    @SuppressLint("MissingPermission")
    fun applyMode(context: Context) {
        val client = ActivityRecognition.getClient(context)
        val pending = activityPendingIntent(context)
        if (AppSettings.trackingMode(context) == TrackingMode.AUTO && hasActivityPermission(context)) {
            val transitions = listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE).flatMap { type ->
                listOf(
                    ActivityTransition.Builder().setActivityType(type)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
                    ActivityTransition.Builder().setActivityType(type)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build()
                )
            }
            runCatching { client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pending) }
        } else {
            runCatching { client.removeActivityTransitionUpdates(pending) }
        }
        // Mode selain manual/otomatis/Bluetooth: hentikan perekaman yang sedang jalan.
        if (AppSettings.trackingMode(context) == TrackingMode.OFF) TrackingService.stop(context)
    }

    private fun activityPendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(
            context, 0, Intent(context, ActivityTransitionReceiver::class.java), flags
        )
    }

    // ---- Pengecekan izin ----
    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBackgroundLocation(context: Context) =
        Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasBluetoothPermission(context: Context) =
        Build.VERSION.SDK_INT < 31 || granted(context, Manifest.permission.BLUETOOTH_CONNECT)

    fun hasActivityPermission(context: Context) =
        Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACTIVITY_RECOGNITION)

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}

/** Pemberitahuan saat Android menolak memulai perekaman dari latar belakang. */
object TrackingNotices {
    private const val ID = 9001

    fun showStartBlocked(context: Context) {
        if (!Notifier.canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, Notifier.CHANNEL_TRIPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.tracking_blocked_title))
            .setContentText(context.getString(R.string.tracking_blocked_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.tracking_blocked_text)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (e: SecurityException) { /* abaikan */ }
    }
}
