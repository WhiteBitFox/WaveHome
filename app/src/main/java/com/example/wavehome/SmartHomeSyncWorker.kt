package com.example.wavehome

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.wavehome.homeapi.HomeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SmartHomeSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = runCatching {
            val db = AppDatabase.getDatabase(applicationContext)
            val homeController = HomeController.getInstance(applicationContext)
            val settingsPrefs = applicationContext.getSharedPreferences(
                APP_SETTINGS_PREFS_NAME,
                Context.MODE_PRIVATE
            )

            val homeControlEnabled = settingsPrefs.getBoolean(
                PREF_HOME_CONTROL_ENABLED,
                true
            )

            SmartHomeDataRecorder(
                context = applicationContext,
                db = db,
                homeController = homeController
            ).collectAndSave(applyHomeControl = homeControlEnabled)
        }

        result.onFailure { error ->
            Log.e("SmartHomeSyncWorker", "Nie udało się wykonać cyklu tła", error)
        }

        if (result.isSuccess) {
            scheduleNextRun(applicationContext)
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val OLD_UNIQUE_PERIODIC_WORK_NAME = "wavehome_smart_home_sync"
        private const val UNIQUE_MINUTE_WORK_NAME = "wavehome_smart_home_sync_minutely"
        private const val BACKOFF_MINUTES = 5L
        private const val SYNC_INTERVAL_MINUTES = 5L

        fun scheduleMinuteLoop(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(OLD_UNIQUE_PERIODIC_WORK_NAME)
            enqueueSyncWork(
                workManager = workManager,
                initialDelayMinutes = 0L,
                policy = ExistingWorkPolicy.KEEP
            )
        }

        private fun scheduleNextRun(context: Context) {
            enqueueSyncWork(
                workManager = WorkManager.getInstance(context.applicationContext),
                initialDelayMinutes = SYNC_INTERVAL_MINUTES,
                policy = ExistingWorkPolicy.APPEND_OR_REPLACE
            )
        }

        private fun enqueueSyncWork(
            workManager: WorkManager,
            initialDelayMinutes: Long,
            policy: ExistingWorkPolicy
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SmartHomeSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_MINUTES,
                    TimeUnit.MINUTES
                )
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_MINUTE_WORK_NAME,
                policy,
                request
            )
        }
    }
}
