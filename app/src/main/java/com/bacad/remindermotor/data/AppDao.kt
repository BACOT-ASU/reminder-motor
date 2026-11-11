package com.bacad.remindermotor.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---- Motor ----
    @Query("SELECT * FROM motor ORDER BY name")
    fun observeMotors(): Flow<List<Motor>>

    @Query("SELECT * FROM motor WHERE id = :id")
    fun observeMotor(id: Long): Flow<Motor?>

    @Insert
    suspend fun insertMotor(motor: Motor): Long

    @Delete
    suspend fun deleteMotor(motor: Motor)

    // ---- Item perawatan ----
    @Query("SELECT * FROM maintenance_item WHERE motorId = :motorId ORDER BY id")
    fun observeItems(motorId: Long): Flow<List<MaintenanceItem>>

    @Insert
    suspend fun insertItem(item: MaintenanceItem): Long

    // ---- Riwayat servis ----
    /** Catatan servis terakhir untuk setiap item milik satu motor. */
    @Query(
        """
        SELECT sr.* FROM service_record sr
        INNER JOIN maintenance_item mi ON mi.id = sr.itemId
        WHERE mi.motorId = :motorId
        AND sr.id = (
            SELECT sr2.id FROM service_record sr2
            WHERE sr2.itemId = sr.itemId
            ORDER BY sr2.date DESC, sr2.id DESC LIMIT 1
        )
        """
    )
    fun observeLatestRecords(motorId: Long): Flow<List<ServiceRecord>>

    @Insert
    suspend fun insertRecord(record: ServiceRecord): Long

    /** Riwayat lengkap satu item, terbaru di atas. */
    @Query("SELECT * FROM service_record WHERE itemId = :itemId ORDER BY date DESC, id DESC")
    fun observeRecords(itemId: Long): Flow<List<ServiceRecord>>

    /** Menghapus titik awal otomatis, dipakai saat pengguna mengisi servis terakhir yang sebenarnya. */
    @Query("DELETE FROM service_record WHERE itemId = :itemId AND action = 'INITIAL'")
    suspend fun deleteInitialRecords(itemId: Long)

    // ---- Versi sekali-baca (tanpa Flow) untuk pengecekan latar belakang ----
    @Query("SELECT * FROM motor ORDER BY name")
    suspend fun getMotors(): List<Motor>

    @Query("SELECT * FROM maintenance_item WHERE motorId = :motorId AND active = 1 ORDER BY id")
    suspend fun getActiveItems(motorId: Long): List<MaintenanceItem>

    @Query(
        """
        SELECT sr.* FROM service_record sr
        INNER JOIN maintenance_item mi ON mi.id = sr.itemId
        WHERE mi.motorId = :motorId
        AND sr.id = (
            SELECT sr2.id FROM service_record sr2
            WHERE sr2.itemId = sr.itemId
            ORDER BY sr2.date DESC, sr2.id DESC LIMIT 1
        )
        """
    )
    suspend fun getLatestRecords(motorId: Long): List<ServiceRecord>

    @Query("UPDATE maintenance_item SET lastNotifiedStatus = :status, lastNotifiedDate = :date WHERE id = :itemId")
    suspend fun updateNotified(itemId: Long, status: String?, date: Long?)

    // ---- Odometer ----
    @Query("UPDATE motor SET odometerKm = :km, odometerDate = :date WHERE id = :motorId")
    suspend fun updateMotorOdometer(motorId: Long, km: Int, date: Long)

    @Insert
    suspend fun insertOdometerLog(log: OdometerLog): Long
}
