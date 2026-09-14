package com.example.wavehome

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "room_fingerprints",
    indices = [Index(value = ["roomName"])]
)
data class RoomFingerprintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomName: String,
    val signalMap: Map<String, Int>,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "room_detections",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["roomName"])
    ]
)
data class RoomDetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val roomName: String,
    val confidence: Double?,
    val signalMap: Map<String, Int>
)

@Dao
interface RoomFingerprintDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(fingerprint: RoomFingerprintEntity)

    @Insert
    suspend fun insertDetection(detection: RoomDetectionEntity)

    @Query("SELECT * FROM room_fingerprints ORDER BY roomName ASC")
    suspend fun getAll(): List<RoomFingerprintEntity>

    @Query("SELECT * FROM room_fingerprints ORDER BY roomName ASC")
    fun observeAll(): Flow<List<RoomFingerprintEntity>>

    @Query("DELETE FROM room_fingerprints WHERE roomName = :roomName")
    suspend fun delete(roomName: String)

    @Query("DELETE FROM room_fingerprints")
    suspend fun deleteAll()

    @Query("DELETE FROM room_detections")
    suspend fun deleteAllDetections()

    @Query("SELECT * FROM room_detections ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestDetections(limit: Int): List<RoomDetectionEntity>
}
