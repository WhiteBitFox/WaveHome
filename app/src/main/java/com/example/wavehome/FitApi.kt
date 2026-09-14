package com.example.wavehome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Model danych reprezentujący jeden dzień snu
data class DailySleepSummary(
    val date: String,
    val totalHours: Long,
    val remainingMinutes: Long,
    val range: String,  // godzinka
    val rawDate: Date  // sort
)

class FitApi(private val context: Context) {
    private val appContext = context.applicationContext
    private val settingsPrefs = appContext.getSharedPreferences(
        APP_SETTINGS_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var dailySleepSummaries by mutableStateOf<List<DailySleepSummary>?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var hasPermissions by mutableStateOf(false)
        private set
    var syncEnabled by mutableStateOf(true)
        private set

    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
        .build()

    init {
        syncEnabled = settingsPrefs.getBoolean(PREF_SYNC_SLEEP_WITH_GOOGLE_FIT, true)
        checkForPermissions()
    }

    fun authorizeAndFetchData(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        isLoading = true
        errorMessage = null
        dailySleepSummaries = null
        syncEnabled = settingsPrefs.getBoolean(PREF_SYNC_SLEEP_WITH_GOOGLE_FIT, true)

        if (!syncEnabled) {
            errorMessage = "Synchronizacja snu z Google Fit jest wyłączona w ustawieniach."
            isLoading = false
            return
        }

        checkForPermissions()

        if (hasPermissions) {
            fetchSleepData()
        } else {
            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .addExtension(fitnessOptions)
                .build()
            val signInClient = GoogleSignIn.getClient(activity, signInOptions)
            launcher.launch(signInClient.signInIntent)
        }
    }

    fun onPermissionsResult() {
        hasPermissions = true
        fetchSleepData()
    }

    fun onPermissionsCancelled() {
        errorMessage = "Dostęp odrzucony. Nie można pobrać danych o śnie."
        isLoading = false
    }

    fun updateSyncEnabled(enabled: Boolean) {
        syncEnabled = enabled
        settingsPrefs.edit()
            .putBoolean(PREF_SYNC_SLEEP_WITH_GOOGLE_FIT, enabled)
            .apply()
        if (!enabled) {
            dailySleepSummaries = null
            isLoading = false
            errorMessage = null
        }
    }

    private fun checkForPermissions() {
        val googleAccount = GoogleSignIn.getAccountForExtension(appContext, fitnessOptions)
        hasPermissions = GoogleSignIn.hasPermissions(googleAccount, fitnessOptions)
    }

    private fun fetchSleepData() {
        isLoading = true
        val account = GoogleSignIn.getAccountForExtension(appContext, fitnessOptions)

        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(14)

        val readRequest = DataReadRequest.Builder()
            .read(DataType.TYPE_SLEEP_SEGMENT)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()

        Fitness.getHistoryClient(appContext, account)
            .readData(readRequest)
            .addOnSuccessListener { response ->
                val dayFormat = SimpleDateFormat("EEEE, dd.MM", Locale.getDefault())
                val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                val segments = response.dataSets.flatMap { dataSet ->
                    dataSet.dataPoints.map { dp ->
                        SleepInterval(
                            startMillis = dp.getStartTime(TimeUnit.MILLISECONDS),
                            endMillis = dp.getEndTime(TimeUnit.MILLISECONDS)
                        )
                    }
                }

                val summaryList = SleepAggregator
                    .summarizeByNight(segments, endTime)
                    .map { summary ->
                        val rangeText = "${hourFormat.format(Date(summary.rangeStartMillis))} - ${hourFormat.format(Date(summary.rangeEndMillis))}"

                        DailySleepSummary(
                            date = dayFormat.format(Date(summary.nightKeyMillis))
                                .replaceFirstChar { it.uppercase() },
                            totalHours = summary.totalMinutes / 60L,
                            remainingMinutes = summary.totalMinutes % 60L,
                            range = rangeText,
                            rawDate = Date(summary.nightKeyMillis)
                        )
                    }

                dailySleepSummaries = summaryList
                isLoading = false
            }
            .addOnFailureListener { e ->
                Log.e("FitApi", "Błąd pobierania", e)
                errorMessage = "Błąd: ${e.localizedMessage}"
                isLoading = false
            }
    }
}
