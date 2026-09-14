package com.example.wavehome

const val SENSOR_FEATURE_COUNT = 11
const val SMART_HOME_OUTPUT_CLASSES = 16
const val MANUAL_DEVICE_OVERRIDE_TTL_MS = 30 * 60 * 1000L

const val OVERRIDE_KIND_MODEL_CORRECTION = "MODEL_CORRECTION"
const val OVERRIDE_KIND_MANUAL_DEVICE = "MANUAL_DEVICE"
const val APP_PREFS_NAME = "AppPrefs"
const val APP_SETTINGS_PREFS_NAME = "app_settings"
const val PREF_HOME_CONTROL_ENABLED = "home_control_enabled"
const val PREF_SYNC_SLEEP_WITH_GOOGLE_FIT = "sync_sleep_with_google_fit"
const val PREF_HOME_WIFI_SSID = "home_wifi_ssid"

enum class SmartDeviceType {
    LIGHT,
    PLUG,
    TV,
    UNKNOWN
}

data class SmartHomeDeviceSnapshot(
    val deviceId: String,
    val name: String,
    val type: SmartDeviceType,
    val isOn: Boolean?,
    val supportsOnOff: Boolean,
    val capturedAt: Long = System.currentTimeMillis()
)

data class SleepSnapshot(
    val minutes: Int,
    val range: String?,
    val slept: Boolean,
    val isSleepingNow: Boolean
)

data class SmartHomeContextSnapshot(
    val timestamp: Long,
    val isHome: Boolean?,
    val roomName: String?,
    val activityType: String?,
    val userStatus: String?,
    val sleep: SleepSnapshot?,
    val weather: WeatherFetcher.WeatherData?,
    val deviceStates: List<SmartHomeDeviceSnapshot>,
    val mlInputVector: FloatArray
)

data class SmartHomeAction(
    val deviceType: SmartDeviceType,
    val desiredOn: Boolean,
    val targetDeviceId: String? = null
)

data class SmartHomePrediction(
    val outputClass: Int,
    val actions: List<SmartHomeAction>,
    val source: PredictionSource
)

enum class PredictionSource {
    MODEL,
    LOCAL_TRAINING,
    USER_OVERRIDE
}
