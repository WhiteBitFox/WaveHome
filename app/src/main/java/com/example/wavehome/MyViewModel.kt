package com.example.wavehome

import android.app.Application
import android.content.Context
import android.net.wifi.ScanResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val appPrefs = application.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)

    // Baza zapisanych pokoi
    val calibratedRooms = mutableStateListOf<RoomFingerprint>()

    // Wynik działania algorytmu
    var currentRoomEstimate by mutableStateOf("Nieznany")

    init {
        viewModelScope.launch {
            db.roomFingerprintDao().observeAll().collectLatest { entities ->
                calibratedRooms.clear()
                calibratedRooms.addAll(
                    entities.map { RoomFingerprint(it.roomName, it.signalMap) }
                )
            }
        }
    }

    // Zapisuje obecny stan sygnałów jako "odcisk palca" pokoju
    fun saveRoomFingerprint(roomName: String, wifiScan: List<ScanResult>, btScan: List<BluetoothDeviceInfo>) {
        if (roomName.isBlank()) return

        val signalMap = combineScans(wifiScan, btScan)

        calibratedRooms.add(RoomFingerprint(roomName.trim(), signalMap))

        viewModelScope.launch(Dispatchers.IO) {
            db.roomFingerprintDao().insert(
                RoomFingerprintEntity(
                    roomName = roomName.trim(),
                    signalMap = signalMap
                )
            )
        }
    }

    // Algorytm sprawdzający, w jakim pokoju jesteśmy
    fun locateMe(wifiScan: List<ScanResult>, btScan: List<BluetoothDeviceInfo>) {
        if (calibratedRooms.isEmpty()) {
            currentRoomEstimate = "Brak kalibracji! Najpierw dodaj pokoje."
            return
        }

        val currentScan = CombinedScan(combineScans(wifiScan, btScan))
        val prediction = IndoorPositioningEngine.predictRoomWithConfidence(
            currentScan.signals,
            calibratedRooms
        )
        currentRoomEstimate = prediction?.roomName ?: "Nie udało się ustalić"

        appPrefs.edit()
            .putString("current_room_name", currentRoomEstimate)
            .putFloat("current_room_confidence", prediction?.confidence?.toFloat() ?: 0f)
            .apply()

        prediction?.let {
            viewModelScope.launch(Dispatchers.IO) {
                db.roomFingerprintDao().insertDetection(
                    RoomDetectionEntity(
                        roomName = it.roomName,
                        confidence = it.confidence,
                        signalMap = currentScan.signals
                    )
                )
            }
        }
    }

    fun clearRoomCalibration() {
        calibratedRooms.clear()
        currentRoomEstimate = "Nieznany"
        appPrefs.edit()
            .remove("current_room_name")
            .remove("current_room_confidence")
            .apply()

        viewModelScope.launch(Dispatchers.IO) {
            db.roomFingerprintDao().deleteAllDetections()
            db.roomFingerprintDao().deleteAll()
        }
    }

    // Łączy skany WiFi i BT w jedną słownikową mapę (Adres MAC -> Siła sygnału)
    private fun combineScans(wifiScan: List<ScanResult>, btScan: List<BluetoothDeviceInfo>): Map<String, Int> {
        val signalMap = mutableMapOf<String, Int>()
        // Używamy BSSID jako unikalnego ID dla WiFi
        wifiScan
            .sortedByDescending { it.level }
            .take(MAX_WIFI_SIGNALS_PER_SCAN)
            .forEach { signalMap[it.BSSID] = it.level }
        // Do fingerprintu bierzemy tylko Bluetooth z realnym RSSI, bez szumu z samych sparowanych urządzeń.
        btScan
            .filter { it.rssi != null }
            .sortedByDescending { it.rssi }
            .take(MAX_BLUETOOTH_SIGNALS_PER_SCAN)
            .forEach { signalMap[it.device.address] = it.rssi!!.toInt() }
        return signalMap
    }

    private companion object {
        const val MAX_WIFI_SIGNALS_PER_SCAN = 18
        const val MAX_BLUETOOTH_SIGNALS_PER_SCAN = 18
    }
}
