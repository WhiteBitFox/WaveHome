package com.example.wavehome

import android.content.Context
import android.util.Log
import com.example.wavehome.homeapi.HomeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmartHomeRecordedCycle(
    val timeLog: TimeLog,
    val deviceStates: List<SmartHomeDeviceSnapshot>,
    val prediction: SmartHomePrediction
)

class SmartHomeDataRecorder(
    context: Context,
    private val db: AppDatabase,
    private val homeController: HomeController
) {
    private val appContext = context.applicationContext
    private val predictor = SmartHomePredictor(appContext, db)
    private val contextCollector = SmartHomeContextCollector(appContext, db, homeController)

    suspend fun collectAndSave(applyHomeControl: Boolean): SmartHomeRecordedCycle? =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = contextCollector.collect()
                val prediction = predictor.predictActions(snapshot.mlInputVector)
                val weatherData = snapshot.weather

                val timeLog = TimeLog(
                    timestamp = snapshot.timestamp,
                    isHome = snapshot.isHome,
                    temperature = weatherData?.temperature,
                    humidity = weatherData?.humidity,
                    windSpeed = weatherData?.windSpeed,
                    precipitation = weatherData?.precipitation,
                    airQuality = null,
                    roomName = snapshot.roomName,
                    activityType = snapshot.activityType,
                    userStatus = snapshot.userStatus,
                    sleepMinutesLastNight = snapshot.sleep?.minutes,
                    sleepRange = snapshot.sleep?.range,
                    sleptLastNight = snapshot.sleep?.slept,
                    isSleeping = snapshot.sleep?.isSleepingNow,
                    mlInputVector = snapshot.mlInputVector,
                    predictedOutputClass = prediction.outputClass,
                    predictionSource = prediction.source.name
                )

                val timeLogId = db.timeLogDao().insert(timeLog)
                saveDeviceStates(timeLogId, snapshot.deviceStates)

                if (applyHomeControl) {
                    applyPredictedActions(prediction, snapshot.deviceStates)
                }

                SmartHomeRecordedCycle(
                    timeLog = timeLog.copy(id = timeLogId),
                    deviceStates = snapshot.deviceStates,
                    prediction = prediction
                )
            }.onFailure { error ->
                Log.e("SmartHomeDataRecorder", "Nie udało się zebrać i zapisać kontekstu", error)
            }.getOrNull()
        }

    private suspend fun saveDeviceStates(
        timeLogId: Long,
        deviceStates: List<SmartHomeDeviceSnapshot>
    ) {
        if (deviceStates.isEmpty()) return

        db.timeLogDao().insertDeviceStates(
            deviceStates.map { state ->
                TimeLogDeviceState(
                    timeLogId = timeLogId,
                    deviceId = state.deviceId,
                    deviceName = state.name,
                    deviceType = state.type.name,
                    isOn = state.isOn,
                    supportsOnOff = state.supportsOnOff,
                    capturedAt = state.capturedAt
                )
            }
        )
    }

    private suspend fun applyPredictedActions(
        prediction: SmartHomePrediction,
        deviceStates: List<SmartHomeDeviceSnapshot>
    ) {
        val activeManualOverrides = db.overrideDao().getActiveManualDeviceOverrides(
            now = System.currentTimeMillis(),
            kind = OVERRIDE_KIND_MANUAL_DEVICE
        )

        homeController.applyActions(
            actions = prediction.actions,
            currentStates = deviceStates
        ) { action, targetDevice ->
            isBlockedByManualOverride(action, targetDevice, activeManualOverrides)
        }
    }

    private fun isBlockedByManualOverride(
        action: SmartHomeAction,
        targetDevice: SmartHomeDeviceSnapshot,
        activeManualOverrides: List<UserOverride>
    ): Boolean {
        return activeManualOverrides.any { override ->
            val desiredOn = override.desiredOn ?: return@any false
            val matchesDevice = override.deviceId == targetDevice.deviceId
            val matchesDeviceType = override.deviceId == null &&
                override.deviceType == targetDevice.type.name

            (matchesDevice || matchesDeviceType) && desiredOn != action.desiredOn
        }
    }
}
