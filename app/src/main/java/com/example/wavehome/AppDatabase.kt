package com.example.wavehome

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Dodajemy encje i konwertery
@Database(
    entities = [
        TimeLog::class,
        TimeLogDeviceState::class,
        UserOverride::class,
        RoomFingerprintEntity::class,
        RoomDetectionEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class) // To pozwoli Room zapisywać FloatArray
abstract class AppDatabase : RoomDatabase() {

    abstract fun timeLogDao(): TimeLogDao
    abstract fun overrideDao(): OverrideDao
    abstract fun roomFingerprintDao(): RoomFingerprintDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "time_log.db" // -> nasza baza
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
