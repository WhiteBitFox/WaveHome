package com.example.wavehome.homeapi

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity // Dodany import!
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wavehome.AppDatabase
import com.example.wavehome.MANUAL_DEVICE_OVERRIDE_TTL_MS
import com.example.wavehome.OVERRIDE_KIND_MANUAL_DEVICE
import com.example.wavehome.SENSOR_FEATURE_COUNT
import com.example.wavehome.SmartHomeDeviceSnapshot
import com.example.wavehome.UserOverride
import com.google.home.HomeDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(context: Context) : ViewModel() {
    private val homeController = HomeController.getInstance(context)
    private val db = AppDatabase.getDatabase(context)

    private val _devices = MutableStateFlow<List<HomeDevice>>(emptyList())
    val devices: StateFlow<List<HomeDevice>> = _devices.asStateFlow()

    private val _deviceStates = MutableStateFlow<List<SmartHomeDeviceSnapshot>>(emptyList())
    val deviceStates: StateFlow<List<SmartHomeDeviceSnapshot>> = _deviceStates.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Wywołujemy z Activity, żeby podpiąć ekran autoryzacji
    fun initHomePermissions(activity: ComponentActivity) {
        // 1. Rejestrujemy Activity jako odbiorcę wyników z okienka systemowego
        homeController.registerPermissions(activity)

        viewModelScope.launch {
            try {
                Log.d("WaveHome_Debug", "Wywoluje ekran uprawnień Google Home...")

                // 2. To wywoła okienko Google: "Wybierz swój dom"
                // forceLaunch = false oznacza, że okienko pokaże się tylko za pierwszym razem
                homeController.requestPermissions(forceLaunch = false)

                // 3. Jeśli jesteśmy tu, to znaczy że użytkownik się zgodził! Zaczynamy pobierać urządzenia.
                Log.d("WaveHome_Debug", "Zalogowano z sukcesem! Odpalam nasłuch urządzeń...")
                observeDevices()

            } catch (e: Exception) {
                Log.e("WaveHome_Debug", "Odmowa dostępu, błąd SHA-1 w chmurze lub brak Test Usera", e)
                _isLoading.value = false
            }
        }
    }

    private fun observeDevices() {
        homeController.startDeviceObservation(viewModelScope) {
            _isLoading.value = false
        }

        viewModelScope.launch {
            homeController.devices.collect { devices ->
                _devices.value = devices
                refreshDeviceStates()
                _isLoading.value = false
            }
        }
    }

    fun toggleDevice(device: HomeDevice) {
        viewModelScope.launch {
            try {
                val currentState = homeController.getCurrentDeviceSnapshots()
                    .firstOrNull { it.deviceId == device.id.toString() }

                if (currentState != null) {
                    setDevicePower(currentState, currentState.isOn != true)
                } else {
                    Log.e("HomeViewModel", "Nie rozpoznano urządzenia do przełączenia.")
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Nie udało się przełączyć urządzenia", e)
            }
        }
    }

    fun toggleDevice(deviceState: SmartHomeDeviceSnapshot) {
        setDevicePower(deviceState, deviceState.isOn != true)
    }

    fun setDevicePower(deviceState: SmartHomeDeviceSnapshot, desiredOn: Boolean) {
        viewModelScope.launch {
            try {
                val success = homeController.setDevicePower(deviceState.deviceId, desiredOn)
                if (success) {
                    saveManualOverride(deviceState, desiredOn)
                    refreshDeviceStates()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Nie udało się ustawić stanu urządzenia", e)
            }
        }
    }

    private suspend fun refreshDeviceStates() {
        _deviceStates.value = homeController.getCurrentDeviceSnapshots(refresh = false)
    }

    private suspend fun saveManualOverride(
        deviceState: SmartHomeDeviceSnapshot,
        desiredOn: Boolean
    ) {
        val now = System.currentTimeMillis()
        db.overrideDao().insert(
            UserOverride(
                sensorSnapshot = FloatArray(SENSOR_FEATURE_COUNT),
                correctOutput = 0,
                timestamp = now,
                kind = OVERRIDE_KIND_MANUAL_DEVICE,
                deviceId = deviceState.deviceId,
                deviceType = deviceState.type.name,
                desiredOn = desiredOn,
                expiresAt = now + MANUAL_DEVICE_OVERRIDE_TTL_MS
            )
        )
    }
}
