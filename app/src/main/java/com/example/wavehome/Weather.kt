package com.example.wavehome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Weather : ComponentActivity() {

    companion object {
        fun newIntent(context: Context) = Intent(context, Weather::class.java)
    }

    // Używamy stanu Compose do przechowywania wyświetlanych danych pogodowych
    private var weatherText by mutableStateOf("Pogoda w Twojej lokalizacji")

    // Przechowujemy współrzędne domu jako zmienne stanu, aby wyświetlić je w UI
    private var homeLat by mutableStateOf<Double?>(null)
    private var homeLng by mutableStateOf<Double?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pobieramy dane zapisane w SharedPreferences
        val sharedPreferences = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
        val savedLat = sharedPreferences.getString("home_lat", null)?.toDoubleOrNull()
        val savedLng = sharedPreferences.getString("home_lng", null)?.toDoubleOrNull()

        if (savedLat != null && savedLng != null) {
            homeLat = savedLat
            homeLng = savedLng
            val targetLocation = LatLng(savedLat, savedLng)
            fetchWeatherData(targetLocation)
        } else {
            showError("Brak zapisanej lokalizacji!")
        }

        setContent {
            // UI oparty na Compose
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Wyświetlamy współrzędne domu
                    Text("Szerokość: ${homeLat ?: "brak danych"}", color = Color.Gray)
                    Text("Długość: ${homeLng ?: "brak danych"}", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    // Wyświetlamy pobrane dane pogodowe
                    Text(text = weatherText, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        if (homeLat != null && homeLng != null) {
                            fetchWeatherData(LatLng(homeLat!!, homeLng!!))
                        } else {
                            showError("Brak zapisanej lokalizacji!")
                        }
                    }) {
                        Text("Odśwież pogodę")
                    }
                }
            }
        }
    }

    private fun fetchWeatherData(location: LatLng) {
        lifecycleScope.launch {
            val weatherResult = getWeather(location)
            weatherResult?.let {
                weatherText = it
            } ?: run {
                weatherText = "Błąd pobierania danych pogodowych"
            }
        }
    }

    private suspend fun getWeather(location: LatLng): String? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.OPEN_WEATHER_API_KEY
                if (apiKey.isBlank()) return@withContext null

                val urlString = "https://api.openweathermap.org/data/2.5/weather?lat=${location.latitude}" +
                        "&lon=${location.longitude}&appid=$apiKey&units=metric"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val result = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(result)
                    val main = jsonObject.getJSONObject("main")
                    val temp = main.getDouble("temp")
                    val humidity = main.getInt("humidity")
                    val windSpeed = jsonObject.getJSONObject("wind").getDouble("speed")

                    var precipitation = 0.0
                    if (jsonObject.has("rain")) {
                        val rain = jsonObject.getJSONObject("rain")
                        precipitation = if (rain.has("1h")) rain.getDouble("1h") else rain.optDouble("3h", 0.0)
                    }
                    "Temperatura: $temp°C\nWilgotność: $humidity%\nWiatr: $windSpeed m/s\nOpady: $precipitation mm"
                } else {
                    // Logowanie kodu odpowiedzi i treści błędu (możesz użyć Log.e do zapisu w logach)
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    println("Error response code: $responseCode")
                    println("Error response: $errorResponse")
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }


    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
