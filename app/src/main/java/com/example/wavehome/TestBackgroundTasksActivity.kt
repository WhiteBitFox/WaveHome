package com.example.wavehome

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.wavehome.ui.theme.WaveHomeTheme
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TestBackgroundTasksActivity : ComponentActivity() {

    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppDatabase.getDatabase(applicationContext)

        setContent {
            WaveHomeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShowDataFromDatabase()
                }
            }
        }
    }

    @Composable
    fun ShowDataFromDatabase() {
        var timeLogs by remember { mutableStateOf<List<TimeLog>>(emptyList()) }

        LaunchedEffect(Unit) {
            lifecycleScope.launch {
                timeLogs = db.timeLogDao().getAll()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Historia pomiarów",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp) // Zastępuje stary Divider
            ) {
                items(timeLogs) { log ->
                    MeasurementItem(log)
                }
            }

            ControlButtons(timeLogs) {
                lifecycleScope.launch {
                    timeLogs = db.timeLogDao().getAll()
                }
            }
        }
    }

    @Composable
    private fun MeasurementItem(log: TimeLog) {
        var deviceStates by remember { mutableStateOf<List<TimeLogDeviceState>>(emptyList()) }

        LaunchedEffect(log.id) {
            deviceStates = db.timeLogDao().getDeviceStatesForTimeLog(log.id)
        }

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                MeasurementRow("Status", if (log.isHome == true) "W domu" else "Poza domem")
                MeasurementRow("Temperatura", "${log.temperature?.let { "%.1f°C".format(it) } ?: "---"}")
                MeasurementRow("Wilgotność", "${log.humidity?.let { "$it%" } ?: "---"}")
                MeasurementRow("Wiatr", "${log.windSpeed?.let { "%.1f m/s".format(it) } ?: "---"}")
                MeasurementRow("Opady", "${log.precipitation?.let { "%.1f mm".format(it) } ?: "---"}")
                MeasurementRow("Powietrze", log.airQuality?.toString() ?: "Brak danych")
                MeasurementRow("Pomieszczenie", log.roomName ?: "Brak danych")
                MeasurementRow("Status użytkownika", log.userStatus ?: "Brak danych")
                MeasurementRow("Czynność", log.activityType ?: "Brak danych")
                MeasurementRow("Spał", if (log.sleptLastNight == true) "Tak" else "Nie")
                MeasurementRow("Teraz śpi", if (log.isSleeping == true) "Tak" else "Nie")
                MeasurementRow(
                    "Sen",
                    log.sleepMinutesLastNight?.let {
                        "${it / 60}h ${it % 60}m${log.sleepRange?.let { range -> " ($range)" } ?: ""}"
                    } ?: "Brak danych"
                )
                MeasurementRow(
                    "Decyzja ML",
                    log.predictedOutputClass?.let {
                        "$it (${log.predictionSource ?: "UNKNOWN"})"
                    } ?: "Brak danych"
                )

                if (deviceStates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Urządzenia",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    deviceStates.forEach { state ->
                        MeasurementRow(
                            label = "${state.deviceName} (${formatDeviceType(state.deviceType)})",
                            value = when (state.isOn) {
                                true -> "Włączone"
                                false -> "Wyłączone"
                                null -> "Brak stanu"
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MeasurementRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    @Composable
    private fun ControlButtons(timeLogs: List<TimeLog>, onUpdate: () -> Unit) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
//            Button(
//                onClick = {
//                    lifecycleScope.launch {
//                        lateinit var sharedPreferences: SharedPreferences
//                        val savedLat = sharedPreferences.getString("home_lat", null)?.toDoubleOrNull()
//                        val savedLng = sharedPreferences.getString("home_lng", null)?.toDoubleOrNull()
//                        val isHome = sharedPreferences.getBoolean("is_home", false)
//
//                        if (savedLat != null && savedLng != null) {
//                            val weatherData = WeatherFetcher.fetchWeather(
//                                applicationContext,
//                                LatLng(savedLat, savedLng)
//                            )
//
//                            val timeLog = TimeLog(
//                                timestamp = System.currentTimeMillis(),
//                                isHome = isHome,
//                                temperature = weatherData?.temperature,
//                                humidity = weatherData?.humidity,
//                                windSpeed = weatherData?.windSpeed,
//                                precipitation = weatherData?.precipitation,
//                                airQuality = null
//                            )
//                            db.timeLogDao().insert(timeLog)
//                        }
//                        onUpdate()
//                    }
//                },
//                modifier = Modifier.fillMaxWidth(),
//                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
//            ) {
//                Text("Dodaj testowy wpis")
//            }
//
//            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    lifecycleScope.launch {
                        db.timeLogDao().deleteAll()
                        onUpdate()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f)))
            ) {
                Text(
                    text = "Wyczyść bazę danych (${timeLogs.size} wpisów)",
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatDeviceType(deviceType: String): String {
        return when (deviceType) {
            SmartDeviceType.LIGHT.name -> "światło"
            SmartDeviceType.PLUG.name -> "gniazdko"
            SmartDeviceType.TV.name -> "TV"
            else -> "inne"
        }
    }
}
