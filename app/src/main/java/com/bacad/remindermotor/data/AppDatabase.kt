package com.bacad.remindermotor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Motor::class, MaintenanceItem::class, ServiceRecord::class, OdometerLog::class, Trip::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** Versi 2: tambah nomor polisi dan foto motor. Data lama tetap aman. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE motor ADD COLUMN plateNumber TEXT")
                db.execSQL("ALTER TABLE motor ADD COLUMN photoPath TEXT")
            }
        }

        /** Versi 3: tabel perjalanan GPS. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trip` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`motorId` INTEGER, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER NOT NULL, " +
                        "`distanceMeters` REAL NOT NULL, " +
                        "`maxSpeedKmh` REAL NOT NULL, " +
                        "`avgSpeedKmh` REAL NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`mode` TEXT NOT NULL)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reminder_motor.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
