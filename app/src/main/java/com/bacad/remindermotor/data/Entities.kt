package com.bacad.remindermotor.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Jenis motor: menentukan item perawatan mana yang tampil. */
enum class MotorType { MATIC, MANUAL }

/** Ganti = komponen diganti. Cek = cukup diperiksa (kampas rem, ban, rantai, aki). */
enum class ItemType { REPLACE, CHECK }

/** Jenis catatan servis. INITIAL = titik awal saat motor ditambahkan. */
enum class ServiceAction { REPLACE, CHECK, INITIAL }

/** Asal angka odometer. GPS disiapkan untuk tahap 6. */
enum class OdometerSource { SPEEDOMETER, GPS }

/**
 * Semua tanggal disimpan sebagai "epoch day" (jumlah hari sejak 1 Jan 1970),
 * supaya mudah dihitung selisihnya.
 */
@Entity(tableName = "motor")
data class Motor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: MotorType,
    val hasRadiator: Boolean,
    val odometerKm: Int,
    val odometerDate: Long,
    val bluetoothTrigger: String? = null,
    /** Nomor polisi, mis. "B 1234 XYZ" (versi DB 2). */
    val plateNumber: String? = null,
    /** Path file foto di penyimpanan internal aplikasi (versi DB 2). */
    val photoPath: String? = null
)

@Entity(
    tableName = "maintenance_item",
    foreignKeys = [ForeignKey(
        entity = Motor::class,
        parentColumns = ["id"],
        childColumns = ["motorId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("motorId")]
)
data class MaintenanceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val motorId: Long,
    /** Kunci item bawaan (mis. "engine_oil"); null untuk item kustom. */
    val key: String?,
    /** Nama untuk item kustom; item bawaan memakai teks terjemahan dari key. */
    val customName: String? = null,
    val type: ItemType,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val active: Boolean = true,
    /** Dipakai tahap notifikasi supaya tidak mengirim pesan dobel. */
    val lastNotifiedStatus: String? = null,
    val lastNotifiedDate: Long? = null
)

@Entity(
    tableName = "service_record",
    foreignKeys = [ForeignKey(
        entity = MaintenanceItem::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class ServiceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val date: Long,
    val km: Int,
    val action: ServiceAction,
    val cost: Long? = null,
    val note: String? = null
)

@Entity(
    tableName = "odometer_log",
    foreignKeys = [ForeignKey(
        entity = Motor::class,
        parentColumns = ["id"],
        childColumns = ["motorId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("motorId")]
)
data class OdometerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val motorId: Long,
    val date: Long,
    val km: Int,
    val source: OdometerSource = OdometerSource.SPEEDOMETER
)

/** Cara perjalanan direkam. */
enum class TrackingMode { OFF, MANUAL, BLUETOOTH, AUTO }

/** PENDING = menunggu konfirmasi, CONFIRMED = km sudah ditambahkan ke motor, NOT_MOTOR = diabaikan. */
enum class TripStatus { PENDING, CONFIRMED, NOT_MOTOR }

/** Satu perjalanan hasil GPS (versi DB 3). Jarak disimpan mentah (belum dikalibrasi). */
@Entity(tableName = "trip")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Motor yang dikonfirmasi (atau yang disarankan saat masih PENDING). */
    val motorId: Long?,
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val status: TripStatus,
    val mode: TrackingMode
)
