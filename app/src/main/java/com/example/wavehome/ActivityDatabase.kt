package com.example.wavehome

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. Podajemy tylko nową encję
@Database(entities = [ActivityLog::class], version = 2, exportSchema = false)
abstract class ActivityDatabase : RoomDatabase() {

    // 2. Podpinamy tylko nowy DAO
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile
        private var INSTANCE: ActivityDatabase? = null

        fun getDatabase(context: Context): ActivityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ActivityDatabase::class.java,
                    "activity_tracking.db" // 3. WAŻNE: Zupełnie inna nazwa pliku!
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
