package com.bacad.remindermotor.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.bacad.remindermotor.MainActivity
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.TrackingMode
import com.bacad.remindermotor.notify.Notifier
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** Keadaan perekaman yang bisa dipantau layar. */
data class TrackingState(
    val running: Boolean = false,
    val mode: TrackingMode = TrackingMode.OFF,
    val motorId: Long? = null,
    val distanceMeters: Double = 0.0
)

/**
 * Foreground service yang merekam jarak dari GPS selama perjalanan.
 * Selama berjalan ada notifikasi permanen (aturan Android untuk akses lokasi di latar belakang).
 */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var mode = TrackingMode.OFF
    private var motorId: Long? = null
    private var startTime = 0L
    private var lastFix: Location? = null
    private var distance = 0.0
    private var maxSpeed = 0.0
    private var lastMoveTime = 0L
    private var lastNotifUpdate = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onLocation(it) }
        }
    }

    private val idleCheck = object : Runnable {
        override fun run() {
            val idleLimit = if (mode == TrackingMode.MANUAL) MANUAL_IDLE_MS else AUTO_IDLE_MS
            if (System.currentTimeMillis() - lastMoveTime > idleLimit) {
                finish()
            } else {
                handler.postDelayed(this, 60_000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val newMode = runCatching { TrackingMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "") }
                    .getOrDefault(TrackingMode.MANUAL)
                val newMotor = intent.getLongExtra(EXTRA_MOTOR_ID, 0L).takeIf { it > 0 }
                if (!_state.value.running) begin(newMode, newMotor)
            }
            ACTION_STOP -> finish()
            else -> if (!_state.value.running) stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun begin(newMode: TrackingMode, newMotor: Long?) {
        mode = newMode
        motorId = newMotor
        startTime = System.currentTimeMillis()
        lastMoveTime = startTime
        distance = 0.0
        maxSpeed = 0.0
        lastFix = null

        Notifier.createChannels(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildOngoing(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        } catch (e: Exception) {
            // Izin lokasi belum diberikan atau Android menolak: berhenti dengan tenang.
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
            return
        }
        _state.value = TrackingState(true, mode, motorId, 0.0)
        handler.postDelayed(idleCheck, 60_000)
    }

    private fun onLocation(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        val prev = lastFix
        lastFix = loc
        if (prev == null) return

        val d = prev.distanceTo(loc).toDouble()
        val seconds = (loc.time - prev.time) / 1000.0
        if (seconds <= 0) return
        val speedKmh = d / seconds * 3.6
        if (speedKmh > MAX_PLAUSIBLE_KMH) return // lompatan GPS, abaikan

        distance += d
        if (speedKmh > maxSpeed) maxSpeed = speedKmh
        if (d > 15) lastMoveTime = System.currentTimeMillis()

        _state.value = _state.value.copy(distanceMeters = distance)
        val now = System.currentTimeMillis()
        if (now - lastNotifUpdate > 15_000) {
            lastNotifUpdate = now
            try {
                NotificationManagerCompat.from(this).notify(NOTIF_ID, buildOngoing())
            } catch (e: SecurityException) { /* abaikan */ }
        }
    }

    private fun finish() {
        if (!_state.value.running) {
            stopSelf()
            return
        }
        handler.removeCallbacks(idleCheck)
        fused.removeLocationUpdates(callback)
        _state.value = TrackingState()

        val m = mode
        val motor = motorId
        val start = startTime
        val end = System.currentTimeMillis()
        val dist = distance
        val max = maxSpeed
        val appContext = applicationContext

        scope.launch {
            val trip = TripRepository(appContext).saveFinishedTrip(m, motor, start, end, dist, max)
            if (trip != null) TripNotifications.onTripSaved(appContext, trip)
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildOngoing(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val km = String.format(Locale.getDefault(), "%.1f", distance / 1000.0)
        return NotificationCompat.Builder(this, Notifier.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_ongoing_title))
            .setContentText(getString(R.string.tracking_ongoing_text, km))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.tracking_stop), stop)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleCheck)
        runCatching { fused.removeLocationUpdates(callback) }
        if (_state.value.running) _state.value = TrackingState()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.bacad.remindermotor.TRACKING_START"
        const val ACTION_STOP = "com.bacad.remindermotor.TRACKING_STOP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_MOTOR_ID = "motorId"
        private const val NOTIF_ID = 9000

        private const val MAX_ACCURACY_M = 35f
        private const val MAX_PLAUSIBLE_KMH = 160.0
        /** Berhenti otomatis jika diam 10 menit (mode otomatis) atau 30 menit (manual). */
        private const val AUTO_IDLE_MS = 10 * 60_000L
        private const val MANUAL_IDLE_MS = 30 * 60_000L

        private val _state = MutableStateFlow(TrackingState())
        val state: StateFlow<TrackingState> = _state

        fun start(context: Context, mode: TrackingMode, motorId: Long?): Boolean {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_MOTOR_ID, motorId ?: 0L)
            return try {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                // Android 12+ bisa menolak start dari latar belakang (mis. optimasi baterai masih aktif).
                false
            }
        }

        fun stop(context: Context) {
            if (!_state.value.running) return
            try {
                context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) { /* abaikan */ }
        }
    }
}
