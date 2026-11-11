package com.bacad.remindermotor.data

import kotlin.math.ceil

enum class ItemStatus { GREEN, YELLOW, RED }

/** Hasil perhitungan untuk satu item: dipakai grid ikon dan layar detail. */
data class ItemState(
    val item: MaintenanceItem,
    val lastRecord: ServiceRecord?,
    /** null jika item tidak punya interval km. */
    val remainingKm: Int?,
    /** null jika item tidak punya interval hari. */
    val remainingDays: Int?,
    val status: ItemStatus
)

object StatusCalc {
    /** Ambang kuning: 10% dari interval. */
    const val WARNING_FRACTION = 0.10

    /**
     * sisa km   = km terakhir diganti + interval km - odometer sekarang
     * sisa hari = tanggal terakhir diganti + interval hari - hari ini
     * Merah jika salah satu <= 0, kuning jika salah satu di bawah ambang, selain itu hijau.
     */
    fun compute(item: MaintenanceItem, last: ServiceRecord?, odometerKm: Int, today: Long): ItemState {
        // Tanpa catatan sama sekali: hitung dari odometer dan hari ini (anggap baru mulai).
        val lastKm = last?.km ?: odometerKm
        val lastDate = last?.date ?: today

        val remainingKm = item.intervalKm?.let { lastKm + it - odometerKm }
        val remainingDays = item.intervalDays?.let { (lastDate + it - today).toInt() }

        val red = (remainingKm != null && remainingKm <= 0) ||
            (remainingDays != null && remainingDays <= 0)

        val yellow = (remainingKm != null && item.intervalKm != null &&
            remainingKm <= threshold(item.intervalKm)) ||
            (remainingDays != null && item.intervalDays != null &&
                remainingDays <= threshold(item.intervalDays))

        val status = when {
            red -> ItemStatus.RED
            yellow -> ItemStatus.YELLOW
            else -> ItemStatus.GREEN
        }
        return ItemState(item, last, remainingKm, remainingDays, status)
    }

    private fun threshold(interval: Int): Int = ceil(interval * WARNING_FRACTION).toInt()
}
