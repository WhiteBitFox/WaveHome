package com.example.wavehome

import java.util.Calendar
import java.util.concurrent.TimeUnit

data class SleepInterval(
    val startMillis: Long,
    val endMillis: Long
)

data class SleepNightSummary(
    val nightKeyMillis: Long,
    val totalMinutes: Int,
    val rangeStartMillis: Long,
    val rangeEndMillis: Long,
    val isSleepingNow: Boolean
)

object SleepAggregator {
    fun summarizeLatestNight(
        intervals: List<SleepInterval>,
        nowMillis: Long
    ): SleepNightSummary? {
        return summarizeByNight(intervals, nowMillis)
            .maxByOrNull { it.rangeEndMillis }
    }

    fun summarizeByNight(
        intervals: List<SleepInterval>,
        nowMillis: Long
    ): List<SleepNightSummary> {
        return intervals
            .filter { it.endMillis > it.startMillis }
            .groupBy { sleepNightKey(it.startMillis) }
            .mapNotNull { (nightKey, nightIntervals) ->
                summarizeNight(nightKey, nightIntervals, nowMillis)
            }
            .sortedByDescending { it.nightKeyMillis }
    }

    private fun summarizeNight(
        nightKeyMillis: Long,
        intervals: List<SleepInterval>,
        nowMillis: Long
    ): SleepNightSummary? {
        val merged = mergeIntervals(intervals)
        if (merged.isEmpty()) return null

        val totalMinutes = merged.sumOf { interval ->
            TimeUnit.MILLISECONDS.toMinutes(interval.endMillis - interval.startMillis)
        }.toInt()

        return SleepNightSummary(
            nightKeyMillis = nightKeyMillis,
            totalMinutes = totalMinutes,
            rangeStartMillis = merged.minOf { it.startMillis },
            rangeEndMillis = merged.maxOf { it.endMillis },
            isSleepingNow = merged.any { nowMillis >= it.startMillis && nowMillis < it.endMillis }
        )
    }

    private fun mergeIntervals(intervals: List<SleepInterval>): List<SleepInterval> {
        val sorted = intervals
            .filter { it.endMillis > it.startMillis }
            .sortedBy { it.startMillis }

        if (sorted.isEmpty()) return emptyList()

        val merged = mutableListOf(sorted.first())
        sorted.drop(1).forEach { interval ->
            val last = merged.last()
            if (interval.startMillis <= last.endMillis) {
                merged[merged.lastIndex] = last.copy(
                    endMillis = maxOf(last.endMillis, interval.endMillis)
                )
            } else {
                merged.add(interval)
            }
        }

        return merged
    }

    private fun sleepNightKey(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            if (get(Calendar.HOUR_OF_DAY) < 12) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
