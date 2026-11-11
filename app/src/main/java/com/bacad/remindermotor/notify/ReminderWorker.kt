package com.bacad.remindermotor.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.AppDatabase
import com.bacad.remindermotor.data.ItemStatus
import com.bacad.remindermotor.data.ItemType
import com.bacad.remindermotor.data.StatusCalc
import com.bacad.remindermotor.ui.itemNameRes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Pengecekan harian:
 * - Kirim notifikasi saat item berubah ke kuning atau merah.
 * - Ulangi tiap [REPEAT_RED_DAYS] hari selama item masih merah.
 * - Ingatkan update odometer jika belum diisi [ODOMETER_REMIND_DAYS] hari.
 * Satu notifikasi per motor supaya tidak banjir.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val force = inputData.getBoolean(KEY_FORCE, false)
        val dao = AppDatabase.get(ctx).dao()
        val today = LocalDate.now().toEpochDay()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        Notifier.createChannels(ctx)

        for (motor in dao.getMotors()) {
            val lastByItem = dao.getLatestRecords(motor.id).associateBy { it.itemId }
            val toAnnounce = mutableListOf<Pair<String, ItemStatus>>()

            for (item in dao.getActiveItems(motor.id)) {
                val state = StatusCalc.compute(item, lastByItem[item.id], motor.odometerKm, today)
                val status = state.status

                if (status == ItemStatus.GREEN) {
                    if (item.lastNotifiedStatus != null) dao.updateNotified(item.id, null, null)
                    continue
                }

                val changed = item.lastNotifiedStatus != status.name
                val repeatRed = status == ItemStatus.RED &&
                    (item.lastNotifiedDate == null || today - item.lastNotifiedDate >= REPEAT_RED_DAYS)

                if (force || changed || repeatRed) {
                    val name = itemNameRes(item.key)?.let { ctx.getString(it) } ?: item.customName ?: "?"
                    val label = when {
                        status == ItemStatus.YELLOW -> ctx.getString(R.string.status_soon)
                        item.type == ItemType.CHECK -> ctx.getString(R.string.status_check_now)
                        else -> ctx.getString(R.string.status_replace_now)
                    }
                    toAnnounce += "$name: $label" to status
                    dao.updateNotified(item.id, status.name, today)
                }
            }

            if (toAnnounce.isNotEmpty()) {
                // Merah dulu, baru kuning.
                val lines = toAnnounce.sortedBy { if (it.second == ItemStatus.RED) 0 else 1 }.map { it.first }
                Notifier.show(
                    ctx,
                    Notifier.CHANNEL_SERVICE,
                    notificationId = (motor.id * 2).toInt(),
                    motorId = motor.id,
                    title = ctx.getString(R.string.notif_service_title, motor.name),
                    text = lines.joinToString("\n")
                )
            }

            // Pengingat update odometer.
            val daysSinceOdo = today - motor.odometerDate
            val lastOdoReminder = prefs.getLong("odo_${motor.id}", Long.MIN_VALUE)
            val odoDue = daysSinceOdo >= ODOMETER_REMIND_DAYS &&
                (lastOdoReminder == Long.MIN_VALUE || today - lastOdoReminder >= ODOMETER_REMIND_DAYS)
            if (odoDue || (force && daysSinceOdo >= ODOMETER_REMIND_DAYS)) {
                Notifier.show(
                    ctx,
                    Notifier.CHANNEL_ODOMETER,
                    notificationId = (motor.id * 2 + 1).toInt(),
                    motorId = motor.id,
                    title = ctx.getString(R.string.notif_odometer_title, motor.name),
                    text = ctx.getString(R.string.notif_odometer_text, daysSinceOdo.toInt())
                )
                prefs.edit().putLong("odo_${motor.id}", today).apply()
            }
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "daily_service_check"
        private const val NOW_NAME = "service_check_now"
        private const val PREFS = "reminder_prefs"
        private const val KEY_FORCE = "force"
        const val REPEAT_RED_DAYS = 3
        const val ODOMETER_REMIND_DAYS = 7

        /** Jam pengecekan harian (bisa diubah di tahap pengaturan). */
        private val CHECK_TIME: LocalTime = LocalTime.of(7, 0)

        /** Jadwalkan pengecekan harian. Aman dipanggil berkali-kali (KEEP). */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(CHECK_TIME)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMinutes = Duration.between(now, next).toMinutes()

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Cek sekarang juga (tombol di aplikasi); force = tampilkan semua yang kuning/merah. */
        fun runNow(context: Context, force: Boolean) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf(KEY_FORCE to force))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
