package com.example.wavehome

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val activityType: String,
    val transitionType: String? = null,
    val userStatus: String? = null,
    val timestamp: Long = System.currentTimeMillis() // Automatycznie zapisuje aktualny czas
)
