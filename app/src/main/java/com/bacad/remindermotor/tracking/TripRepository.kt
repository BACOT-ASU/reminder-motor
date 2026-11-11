package com.bacad.remindermotor.tracking

import android.content.Context
import androidx.room.withTransaction
import com.bacad.remindermotor.data.AppDatabase
import com.bacad.remindermotor.data.OdometerLog
import com.bacad.remindermotor.data.OdometerSource
import com.bacad.remindermotor.data.TrackingMode
import com.bacad.remindermotor.data.Trip
import com.bacad.remindermotor.data.TripStatus
import java.time.LocalDate

/** Simpan dan konfirmasi perjalanan GPS. Dipakai service, receiver, dan layar. */
class TripRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val dao = db.dao()

    fun observeTrips() = dao.observeTrips()
    fun observePendingCount() = dao.observePendingTripCount()

    /**
     * Simpan perjalanan yang baru selesai.
     * @return trip yang tersimpan, atau null jika terlalu pendek (diabaikan).
     */
    suspend fun saveFinishedTrip(
        mode: TrackingMode,
        suggestedMotorId: Long?,
        startTime: Long,
        endTime: Long,
        distanceMeters: Double,
        maxSpeedKmh: Double
    ): Trip? {
        if (distanceMeters < MIN_TRIP_METERS) return null

        val hours = (endTime - startTime) / 3_600_000.0
        val avg = if (hours > 0) distanceMeters / 1000.0 / hours else 0.0

        // Filter: kecepatan rata-rata khas kereta/jalan tol -> anggap bukan motor.
        val looksNotMotor = avg > MAX_MOTOR_AVG_KMH

        val manualConfirmed = mode == TrackingMode.MANUAL && suggestedMotorId != null && !looksNotMotor
        // Mode manual dengan motor terpilih: disimpan PENDING lalu langsung dikonfirmasi di bawah.
        val status = if (looksNotMotor) TripStatus.NOT_MOTOR else TripStatus.PENDING
        val trip = Trip(
            motorId = suggestedMotorId,
            startTime = startTime,
            endTime = endTime,
            distanceMeters = distanceMeters,
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = avg,
            status = status,
            mode = mode
        )
        val id = dao.insertTrip(trip)
        if (manualConfirmed) confirm(id, suggestedMotorId)
        return dao.getTrip(id)
    }

    /**
     * Konfirmasi perjalanan. motorId = null berarti "Bukan motor".
     * Aman dipanggil dua kali: perjalanan yang sudah diputuskan tidak diproses ulang.
     */
    suspend fun confirm(tripId: Long, motorId: Long?) {
        val trip = dao.getTrip(tripId) ?: return
        if (trip.status != TripStatus.PENDING) return

        if (motorId == null) {
            dao.updateTripStatus(tripId, TripStatus.NOT_MOTOR.name, trip.motorId)
            return
        }
        val motor = dao.getMotor(motorId) ?: return
        val km = Calibration.convertAndAccumulate(context, motorId, trip.distanceMeters)
        db.withTransaction {
            if (km > 0) {
                dao.addOdometerKm(motorId, km)
                dao.insertOdometerLog(
                    OdometerLog(
                        motorId = motorId,
                        date = LocalDate.now().toEpochDay(),
                        km = motor.odometerKm + km,
                        source = OdometerSource.GPS
                    )
                )
            }
            dao.updateTripStatus(tripId, TripStatus.CONFIRMED.name, motorId)
        }
    }

    companion object {
        /** Perjalanan di bawah 300 m diabaikan (jalan kaki, GPS goyang). */
        const val MIN_TRIP_METERS = 300.0
        /** Rata-rata di atas 80 km/jam dianggap kereta/mobil tol, bukan motor harian. */
        const val MAX_MOTOR_AVG_KMH = 80.0
    }
}
