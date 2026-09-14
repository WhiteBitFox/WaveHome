package com.example.wavehome

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.wavehome.homeapi.HomeController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SmartHomeContextCollector(
    private val context: Context,
    private val db: AppDatabase,
    private val homeController: HomeController
) {
    private val appContext = context.applicationContext
    private val appPrefs = appContext.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    private val roomPositionProvider = RoomPositionProvider(appContext, db)
    private val sleepDataSource = SleepDataSource(appContext)

    suspend fun collect(): SmartHomeContextSnapshot = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val homeLocation = getHomeLocation()
        val isHome = resolveIsHome(homeLocation)
        val weather = homeLocation?.let {
            WeatherFetcher.fetchWeather(appContext, LatLng(it.first, it.second))
        }
        val roomName = roomPositionProvider.estimateCurrentRoom()
        val activityDao = ActivityDatabase.getDatabase(appContext).activityLogDao()
        val latestActivity = activityDao.getLatestLog()
        val latestEnteredStatus = activityDao.getLatestEnteredStatusLog() ?: latestActivity
        val sleep = sleepDataSource.getLastSleepSnapshot()
        val userStatus = UserStatusResolver.resolve(latestEnteredStatus, sleep)
        val deviceStates = homeController.getCurrentDeviceSnapshots()

        val partialSnapshot = SmartHomeContextSnapshot(
            timestamp = timestamp,
            isHome = isHome,
            roomName = roomName,
            activityType = latestActivity?.activityType,
            userStatus = userStatus,
            sleep = sleep,
            weather = weather,
            deviceStates = deviceStates,
            mlInputVector = FloatArray(SENSOR_FEATURE_COUNT)
        )

        partialSnapshot.copy(
            mlInputVector = SmartHomeFeatureBuilder.build(partialSnapshot)
        )
    }

    private fun getHomeLocation(): Pair<Double, Double>? {
        val savedLat = appPrefs.getString("home_lat", null)?.toDoubleOrNull()
        val savedLng = appPrefs.getString("home_lng", null)?.toDoubleOrNull()
        if (savedLat == null || savedLng == null) return null
        return savedLat to savedLng
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveIsHome(homeLocation: Pair<Double, Double>?): Boolean? {
        if (homeLocation == null) return appPrefs.getBoolean("is_home", false)

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) return appPrefs.getBoolean("is_home", false)

        return try {
            val current = LocationServices.getFusedLocationProviderClient(appContext)
                .lastLocation
                .await()
                ?: return appPrefs.getBoolean("is_home", false)

            val currentLocation = Location("").apply {
                latitude = current.latitude
                longitude = current.longitude
            }
            val savedHome = Location("").apply {
                latitude = homeLocation.first
                longitude = homeLocation.second
            }
            val isHome = currentLocation.distanceTo(savedHome) < 100
            appPrefs.edit().putBoolean("is_home", isHome).apply()
            isHome
        } catch (e: Exception) {
            appPrefs.getBoolean("is_home", false)
        }
    }
}
