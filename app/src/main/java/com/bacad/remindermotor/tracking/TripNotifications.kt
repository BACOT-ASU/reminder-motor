package com.bacad.remindermotor.tracking

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bacad.remindermotor.MainActivity
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.AppDatabase
import com.bacad.remindermotor.data.Trip
import com.bacad.remindermotor.data.TripStatus
import com.bacad.remindermotor.notify.Notifier
import java.util.Locale

/** Notifikasi "Perjalanan 12 km tadi pakai motor apa?" beserta tombol jawabannya. */
object TripNotifications {
    const val EXTRA_OPEN_TRIPS = "openTrips"
    private const val BASE_ID = 10_000

    fun notificationId(tripId: Long) = BASE_ID + (tripId % 50_000).toInt()

    suspend fun onTripSaved(context: Context, trip: Trip) {
        if (trip.status != TripStatus.PENDING) return
        if (!Notifier.canNotify(context)) return

        val motors = AppDatabase.get(context).dao().getMotors()
        val km = String.format(Locale.getDefault(), "%.1f", trip.distanceMeters / 1000.0)
        val nid = notificationId(trip.id)

        val open = PendingIntent.getActivity(
            context, nid,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_TRIPS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, Notifier.CHANNEL_TRIPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.trip_confirm_title, km))
            .setContentText(context.getString(R.string.trip_confirm_text))
            .setContentIntent(open)
            .setAutoCancel(true)

        // Maksimal 3 tombol: jika motor <= 2, tampilkan nama motor + "Bukan motor".
        if (motors.size <= 2) {
            motors.forEachIndexed { i, m ->
                val label = m.plateNumber?.takeIf { it.isNotBlank() } ?: m.name
                builder.addAction(0, label, actionIntent(context, trip.id, m.id, nid * 10 + i))
            }
        } else {
            builder.addAction(0, context.getString(R.string.trip_choose_motor), open)
        }
        builder.addAction(0, context.getString(R.string.trip_not_motor), actionIntent(context, trip.id, 0L, nid * 10 + 9))

        try {
            NotificationManagerCompat.from(context).notify(nid, builder.build())
        } catch (e: SecurityException) { /* izin dicabut */ }
    }

    private fun actionIntent(context: Context, tripId: Long, motorId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, TripActionReceiver::class.java)
                .putExtra(TripActionReceiver.EXTRA_TRIP_ID, tripId)
                .putExtra(TripActionReceiver.EXTRA_MOTOR_ID, motorId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun cancel(context: Context, tripId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(tripId))
    }
}
