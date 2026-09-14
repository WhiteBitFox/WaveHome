package com.example.wavehome

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherFetcher {
    data class WeatherData(
        val temperature: Double,
        val humidity: Int,
        val windSpeed: Double,
        val precipitation: Double
    )

    suspend fun fetchWeather(context: Context, location: LatLng): WeatherData? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.OPEN_WEATHER_API_KEY
                if (apiKey.isBlank()) return@withContext null

                val urlString = "https://api.openweathermap.org/data/2.5/weather?" +
                        "lat=${location.latitude}&lon=${location.longitude}" +
                        "&appid=$apiKey&units=metric"

                val connection = URL(urlString).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    parseWeatherJson(json)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun parseWeatherJson(json: String): WeatherData {
        val obj = JSONObject(json)
        return WeatherData(
            temperature = obj.getJSONObject("main").getDouble("temp"),
            humidity = obj.getJSONObject("main").getInt("humidity"),
            windSpeed = obj.getJSONObject("wind").getDouble("speed"),
            precipitation = obj.optJSONObject("rain")?.run {
                if (has("1h")) getDouble("1h") else optDouble("3h", 0.0)
            } ?: 0.0
        )
    }
}
