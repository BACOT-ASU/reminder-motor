package com.bacad.remindermotor.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Motor::class, MaintenanceItem::class, ServiceRecord::class, OdometerLog::class],
    version = 2,
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

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reminder_motor.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
