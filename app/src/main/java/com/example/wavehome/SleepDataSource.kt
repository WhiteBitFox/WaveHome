package com.example.wavehome

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SleepDataSource(private val context: Context) {
    private val appContext = context.applicationContext
    private val settingsPrefs = appContext.getSharedPreferences(
        APP_SETTINGS_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
        .build()

    suspend fun getLastSleepSnapshot(): SleepSnapshot? = withContext(Dispatchers.IO) {
        try {
            if (!isSyncEnabled()) return@withContext null

            val account = GoogleSignIn.getAccountForExtension(appContext, fitnessOptions)
            if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) return@withContext null

            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.DAYS.toMillis(7)

            val request = DataReadRequest.Builder()
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build()

            val response = Fitness.getHistoryClient(appContext, account)
                .readData(request)
                .await()

            val segments = response.dataSets.flatMap { dataSet ->
                dataSet.dataPoints.map { dataPoint ->
                    SleepInterval(
                        startMillis = dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                        endMillis = dataPoint.getEndTime(TimeUnit.MILLISECONDS)
                    )
                }
            }

            if (segments.isEmpty()) return@withContext null

            val latestNight = SleepAggregator.summarizeLatestNight(segments, endTime)
                ?: return@withContext null
            val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            SleepSnapshot(
                minutes = latestNight.totalMinutes,
                range = "${hourFormat.format(Date(latestNight.rangeStartMillis))} - ${hourFormat.format(Date(latestNight.rangeEndMillis))}",
                slept = latestNight.totalMinutes > 0,
                isSleepingNow = latestNight.isSleepingNow
            )
        } catch (e: Exception) {
            Log.e("SleepDataSource", "Nie udało się pobrać danych snu", e)
            null
        }
    }

    fun isSyncEnabled(): Boolean {
        return settingsPrefs.getBoolean(PREF_SYNC_SLEEP_WITH_GOOGLE_FIT, true)
    }

    fun setSyncEnabled(enabled: Boolean) {
        settingsPrefs.edit()
            .putBoolean(PREF_SYNC_SLEEP_WITH_GOOGLE_FIT, enabled)
            .apply()
    }
}
