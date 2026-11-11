package com.bacad.remindermotor.tracking

import android.content.Context
import kotlin.math.roundToInt

/**
 * Kalibrasi GPS terhadap spidometer, per motor.
 *
 * Spidometer = patokan utama. Setiap kali km spido diinput:
 *   faktor = (km spido baru - km spido sebelumnya) / km GPS mentah yang terkumpul di antaranya
 * lalu km GPS berikutnya dikalikan faktor ini. Faktor dibatasi 0,8–1,25 supaya
 * satu input salah ketik tidak merusak hitungan.
 */
object Calibration {
    private const val PREFS = "gps_calibration"
    private const val MIN_FACTOR = 0.8
    private const val MAX_FACTOR = 1.25
    /** Kalibrasi baru dihitung jika GPS sudah mengumpulkan minimal 20 km. */
    private const val MIN_RAW_KM_FOR_CALIBRATION = 20.0

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun factor(c: Context, motorId: Long): Double =
        prefs(c).getFloat("factor_$motorId", 1f).toDouble()

    /**
     * Mengubah jarak GPS mentah menjadi km bulat yang ditambahkan ke odometer.
     * Sisa pecahan disimpan supaya perjalanan pendek tidak hilang karena pembulatan.
     */
    fun convertAndAccumulate(c: Context, motorId: Long, rawMeters: Double): Int {
        val p = prefs(c)
        val corrected = rawMeters / 1000.0 * factor(c, motorId) + p.getFloat("remainder_$motorId", 0f)
        val whole = corrected.toInt()
        p.edit()
            .putFloat("remainder_$motorId", (corrected - whole).toFloat())
            .putFloat("raw_since_$motorId", p.getFloat("raw_since_$motorId", 0f) + (rawMeters / 1000.0).toFloat())
            .apply()
        return whole
    }

    /** Dipanggil saat km spidometer diinput. [previousSpeedoKm] = input spido sebelumnya. */
    fun onSpeedometer(c: Context, motorId: Long, previousSpeedoKm: Int?, newSpeedoKm: Int) {
        val p = prefs(c)
        val rawKm = p.getFloat("raw_since_$motorId", 0f).toDouble()
        val edit = p.edit()
        if (previousSpeedoKm != null && rawKm >= MIN_RAW_KM_FOR_CALIBRATION) {
            val realKm = (newSpeedoKm - previousSpeedoKm).toDouble()
            if (realKm > 0) {
                val f = (realKm / rawKm).coerceIn(MIN_FACTOR, MAX_FACTOR)
                // Rata-rata dengan faktor lama supaya perubahannya halus.
                val blended = (factor(c, motorId) + f) / 2
                edit.putFloat("factor_$motorId", blended.toFloat())
            }
        }
        edit.putFloat("raw_since_$motorId", 0f).putFloat("remainder_$motorId", 0f).apply()
    }

    /** Persentase koreksi untuk ditampilkan, mis. "+4%". */
    fun factorPercent(c: Context, motorId: Long): Int = ((factor(c, motorId) - 1.0) * 100).roundToInt()
}
