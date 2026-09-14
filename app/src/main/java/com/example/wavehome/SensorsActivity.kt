package com.example.wavehome

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.collections.set
import android.hardware.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

class SensorsActivity : ComponentActivity(),SensorEventListener {
    companion object {
        fun newIntent(context: Context) = Intent(context, SensorsActivity::class.java)
    }

    private lateinit var sensorManager: SensorManager
    private var sensorsData = mutableStateOf(mapOf<String, List<Float>>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicjalizacja SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Pobranie wszystkich dostępnych czujników
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)

        // Rejestracja listenera dla każdego czujnika
        allSensors.forEach { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Ustawienie interfejsu użytkownika za pomocą Jetpack Compose
        setContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SensorListDisplay(sensorsData.value)
                }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            // Nazwa czujnika
            val sensorName = it.sensor.name

            // Wartości z czujnika
            val values = it.values.toList()

            // Aktualizacja stanu czujników
            sensorsData.value = sensorsData.value.toMutableMap().apply {
                this[sensorName] = values
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignorujemy zmiany dokładności
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }
}

@Composable
fun SensorListDisplay(sensorsData: Map<String, List<Float>>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        sensorsData.forEach { (sensorName, values) ->
            Text(text = "Sensor: $sensorName", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            values.forEachIndexed { index, value ->
                Text(text = "Value $index: $value", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
