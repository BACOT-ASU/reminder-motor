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

    // ---- Odometer ----
    @Insert
    suspend fun insertOdometerLog(log: OdometerLog): Long
}
