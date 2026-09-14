package com.example.wavehome

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

//OLD
//@Entity(indices = [Index(value = ["timestamp"], unique = true)])
//data class TimeLog(
//    @PrimaryKey(autoGenerate = true) val id: Long = 0,
//    val timestamp: Long
//)

//@Dao
//interface TimeLogDao {
//    @Insert
//    suspend fun insert(timeLog: TimeLog)
//
//    @Query("SELECT * FROM timelog ORDER BY id DESC")
//    suspend fun getAll(): List<TimeLog>
//
//    @Query("DELETE FROM timelog")
//    suspend fun deleteAll()
//
//}

//NEW
@Entity(tableName = "time_logs")
data class TimeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val isHome: Boolean?,
    val temperature: Double?,
    val humidity: Int?,
    val windSpeed: Double?,
    val precipitation: Double?,
    val airQuality: Int?,
    val roomName: String? = null,
    val activityType: String? = null,
    val userStatus: String? = null,
    val sleepMinutesLastNight: Int? = null,
    val sleepRange: String? = null,
    val sleptLastNight: Boolean? = null,
    val isSleeping: Boolean? = null,
    val mlInputVector: FloatArray? = null,
    val predictedOutputClass: Int? = null,
    val predictionSource: String? = null
)

@Entity(
    tableName = "time_log_device_states",
    foreignKeys = [
        ForeignKey(
            entity = TimeLog::class,
            parentColumns = ["id"],
            childColumns = ["timeLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["timeLogId"]),
        Index(value = ["deviceId"])
    ]
)
data class TimeLogDeviceState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeLogId: Long,
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val isOn: Boolean?,
    val supportsOnOff: Boolean,
    val capturedAt: Long
)

@Dao
interface TimeLogDao {
    @Insert
    suspend fun insert(timeLog: TimeLog): Long

    @Insert
    suspend fun insertDeviceStates(states: List<TimeLogDeviceState>)

    @Query("SELECT * FROM time_logs ORDER BY id DESC")
    suspend fun getAll(): List<TimeLog>

    @Query("SELECT * FROM time_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestEntries(limit: Int): List<TimeLog>

    @Query("SELECT * FROM time_log_device_states WHERE timeLogId = :timeLogId ORDER BY id ASC")
    suspend fun getDeviceStatesForTimeLog(timeLogId: Long): List<TimeLogDeviceState>

    @Query("DELETE FROM time_logs")
    suspend fun deleteAll()

    @Query("SELECT * FROM time_logs WHERE id = :id")
    suspend fun getById(id: Long): TimeLog?

    @Query("SELECT * FROM time_logs WHERE timestamp BETWEEN :start AND :end")
    suspend fun getByDateRange(start: Long, end: Long): List<TimeLog>

    @Query("SELECT * FROM time_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): TimeLog?

    @Delete
    suspend fun delete(timeLog: TimeLog)
}
