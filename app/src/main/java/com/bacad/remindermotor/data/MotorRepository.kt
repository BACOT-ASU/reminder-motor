package com.bacad.remindermotor.data

import androidx.room.withTransaction
import java.time.LocalDate

class MotorRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    fun observeMotors() = dao.observeMotors()
    fun observeMotor(id: Long) = dao.observeMotor(id)
    fun observeItems(motorId: Long) = dao.observeItems(motorId)
    fun observeLatestRecords(motorId: Long) = dao.observeLatestRecords(motorId)

    /**
     * Menambah motor beserta item bawaannya.
     * Untuk sementara semua item dianggap "mulai hari ini" (catatan INITIAL);
     * layar onboarding untuk mengisi tanggal ganti terakhir menyusul di tahap 3.
     */
    suspend fun addMotor(name: String, type: MotorType, hasRadiator: Boolean, odometerKm: Int): Long {
        val today = LocalDate.now().toEpochDay()
        return db.withTransaction {
            val motorId = dao.insertMotor(
                Motor(
                    name = name,
                    type = type,
                    hasRadiator = hasRadiator,
                    odometerKm = odometerKm,
                    odometerDate = today
                )
            )
            dao.insertOdometerLog(OdometerLog(motorId = motorId, date = today, km = odometerKm))
            DefaultItems.forMotor(type, hasRadiator).forEach { def ->
                val itemId = dao.insertItem(
                    MaintenanceItem(
                        motorId = motorId,
                        key = def.key,
                        type = def.type,
                        intervalKm = def.intervalKm,
                        intervalDays = def.intervalDays
                    )
                )
                dao.insertRecord(
                    ServiceRecord(itemId = itemId, date = today, km = odometerKm, action = ServiceAction.INITIAL)
                )
            }
            motorId
        }
    }

    suspend fun deleteMotor(motor: Motor) = dao.deleteMotor(motor)
}
