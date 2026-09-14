package com.example.wavehome

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_overrides")
data class UserOverride(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sensorSnapshot: FloatArray, // Tu zapiszemy stan 11 czujników
    val correctOutput: Int,         // Tu zapiszemy, co użytkownik wybrał (0-15)
    val timestamp: Long = System.currentTimeMillis(),
    val kind: String = OVERRIDE_KIND_MODEL_CORRECTION,
    val deviceId: String? = null,
    val deviceType: String? = null,
    val desiredOn: Boolean? = null,
    val expiresAt: Long? = null
)
