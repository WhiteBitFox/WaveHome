package com.example.wavehome

import kotlin.math.pow
import kotlin.math.sqrt

data class RoomPrediction(
    val roomName: String,
    val confidence: Double,
    val distance: Double
)

object IndoorPositioningEngine {
    fun predictRoom(
        currentSignals: Map<String, Int>,
        calibratedRooms: List<RoomFingerprint>
    ): String? = predictRoomWithConfidence(currentSignals, calibratedRooms)?.roomName

    fun predictRoomWithConfidence(
        currentSignals: Map<String, Int>,
        calibratedRooms: List<RoomFingerprint>
    ): RoomPrediction? {
        if (currentSignals.isEmpty() || calibratedRooms.isEmpty()) return null

        val rankedRooms = calibratedRooms
            .map { fingerprint -> fingerprint.roomName to distance(currentSignals, fingerprint.signalMap) }
            .groupBy({ it.first }, { it.second })
            .map { (roomName, distances) ->
                roomName to distances.sorted().take(3).average()
            }
            .sortedBy { it.second }

        val best = rankedRooms.firstOrNull() ?: return null
        val secondDistance = rankedRooms.getOrNull(1)?.second
        val confidence = if (secondDistance == null || secondDistance <= 0.0) {
            (1.0 / (1.0 + best.second / 100.0)).coerceIn(0.0, 1.0)
        } else {
            ((secondDistance - best.second) / secondDistance).coerceIn(0.0, 1.0)
        }

        return RoomPrediction(
            roomName = best.first,
            confidence = confidence,
            distance = best.second
        )
    }

    private fun distance(
        currentSignals: Map<String, Int>,
        savedSignals: Map<String, Int>
    ): Double {
        val allMacAddresses = currentSignals.keys + savedSignals.keys
        if (allMacAddresses.isEmpty()) return Double.MAX_VALUE

        var distanceSum = 0.0
        var overlapCount = 0

        for (mac in allMacAddresses) {
            val liveRssi = currentSignals[mac] ?: -100
            val savedRssi = savedSignals[mac] ?: -100
            if (currentSignals.containsKey(mac) && savedSignals.containsKey(mac)) {
                overlapCount++
            }
            distanceSum += (liveRssi - savedRssi).toDouble().pow(2)
        }

        val rmsDistance = sqrt(distanceSum / allMacAddresses.size)
        val expectedOverlap = minOf(currentSignals.size, savedSignals.size).coerceAtLeast(1)
        val overlapRatio = overlapCount.toDouble() / expectedOverlap
        val lowOverlapPenalty = if (overlapRatio < MIN_OVERLAP_RATIO) {
            LOW_OVERLAP_PENALTY
        } else {
            0.0
        }

        return rmsDistance + lowOverlapPenalty
    }

    private const val MIN_OVERLAP_RATIO = 0.15
    private const val LOW_OVERLAP_PENALTY = 25.0
}
