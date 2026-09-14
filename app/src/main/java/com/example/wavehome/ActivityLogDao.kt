package com.example.wavehome

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow // WAŻNY IMPORT

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insertLog(log: ActivityLog)

    // Flow sprawi, że interfejs sam zauważy nowe wpisy! (Zwróć uwagę na brak słowa 'suspend')
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<ActivityLog>>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLog(): ActivityLog?

    @Query(
        "SELECT * FROM activity_logs " +
            "WHERE userStatus IS NOT NULL AND (transitionType IS NULL OR transitionType = 'ENTER') " +
            "ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun getLatestEnteredStatusLog(): ActivityLog?
}
