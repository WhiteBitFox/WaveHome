package com.example.wavehome

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OverrideDao {
    @Query("SELECT * FROM user_overrides")
    suspend fun getAll(): List<UserOverride>

    @Query("SELECT * FROM user_overrides WHERE kind = :kind")
    suspend fun getByKind(kind: String): List<UserOverride>

    @Query(
        "SELECT * FROM user_overrides " +
            "WHERE kind = :kind AND expiresAt IS NOT NULL AND expiresAt > :now " +
            "ORDER BY timestamp DESC"
    )
    suspend fun getActiveManualDeviceOverrides(
        now: Long,
        kind: String
    ): List<UserOverride>

    @Insert
    suspend fun insert(override: UserOverride)

    @Query("DELETE FROM user_overrides")
    suspend fun clearAll()
}
