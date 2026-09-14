package com.example.wavehome

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult as BleScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class RoomPositionProvider(
    private val context: Context,
    private val db: AppDatabase
) {
    private val appContext = context.applicationContext
    private val appPrefs = appContext.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun estimateCurrentRoom(): String? = withContext(Dispatchers.IO) {
        val fingerprints = db.roomFingerprintDao().getAll()
            .map { RoomFingerprint(it.roomName, it.signalMap) }
        if (fingerprints.isEmpty()) return@withContext appPrefs.getString("current_room_name", null)

        val currentSignals = getCurrentSignals()
        val prediction = IndoorPositioningEngine.predictRoomWithConfidence(currentSignals, fingerprints)
        val room = prediction?.roomName ?: appPrefs.getString("current_room_name", null)

        if (!room.isNullOrBlank()) {
            appPrefs.edit()
                .putString("current_room_name", room)
                .putFloat("current_room_confidence", prediction?.confidence?.toFloat() ?: 0f)
                .apply()
            db.roomFingerprintDao().insertDetection(
                RoomDetectionEntity(
                    roomName = room,
                    confidence = prediction?.confidence,
                    signalMap = currentSignals
                )
            )
        }

        room
    }

    private suspend fun getCurrentSignals(): Map<String, Int> = coroutineScope {
        val wifiSignals = async { getCurrentWifiSignals() }
        val bluetoothSignals = async { getCurrentBluetoothSignals() }
        wifiSignals.await() + bluetoothSignals.await()
    }

    private suspend fun getCurrentWifiSignals(): Map<String, Int> {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) return emptyMap()

        return try {
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.startScan()
            delay(WIFI_SCAN_SETTLE_MS)
            wifiManager.scanResults
                .distinctBy { it.BSSID }
                .sortedByDescending { it.level }
                .take(MAX_WIFI_SIGNALS_PER_SCAN)
                .associate { it.BSSID to it.level }
        } catch (e: SecurityException) {
            emptyMap()
        }
    }

    private suspend fun getCurrentBluetoothSignals(): Map<String, Int> {
        if (!hasBluetoothScanPermission()) return emptyMap()

        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        val bluetoothAdapter = bluetoothManager?.adapter ?: return emptyMap()
        if (!bluetoothAdapter.isEnabled) return emptyMap()

        val discoveredSignals = withTimeoutOrNull(BLUETOOTH_DISCOVERY_TIMEOUT_MS + 500L) {
            discoverBluetoothSignals(bluetoothAdapter)
        }.orEmpty()

        return discoveredSignals
            .entries
            .sortedByDescending { it.value }
            .take(MAX_BLUETOOTH_SIGNALS_PER_SCAN)
            .associate { it.key to it.value }
    }

    private suspend fun discoverBluetoothSignals(
        bluetoothAdapter: BluetoothAdapter
    ): Map<String, Int> = suspendCancellableCoroutine { continuation ->
        val signals = mutableMapOf<String, Int>()
        var completed = false
        lateinit var receiver: BroadcastReceiver
        var bleCallback: ScanCallback? = null

        fun finish() {
            if (completed) return
            completed = true
            try {
                appContext.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver could already be unregistered by cancellation.
            }
            try {
                if (hasBluetoothScanPermission()) bluetoothAdapter.cancelDiscovery()
            } catch (e: SecurityException) {
                // Ignore and return collected results.
            }
            try {
                if (hasBluetoothScanPermission()) {
                    bleCallback?.let { bluetoothAdapter.bluetoothLeScanner?.stopScan(it) }
                }
            } catch (e: SecurityException) {
                // Ignore and return collected results.
            }
            if (continuation.isActive) continuation.resume(signals)
        }

        bleCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: BleScanResult?) {
                result?.device?.let { device ->
                    try {
                        val address = device.address
                        if (!address.isNullOrBlank()) {
                            signals[address] = result.rssi
                        }
                    } catch (e: SecurityException) {
                        // Address can require Bluetooth permission on newer Android versions.
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<BleScanResult>?) {
                results.orEmpty().forEach { result ->
                    try {
                        val address = result.device?.address
                        if (!address.isNullOrBlank()) {
                            signals[address] = result.rssi
                        }
                    } catch (e: SecurityException) {
                        // Address can require Bluetooth permission on newer Android versions.
                    }
                }
            }
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        val rssi = intent.getShortExtra(
                            BluetoothDevice.EXTRA_RSSI,
                            DEFAULT_MISSING_RSSI.toShort()
                        ).toInt()

                        try {
                            val address = device?.address
                            if (!address.isNullOrBlank()) {
                                signals[address] = rssi
                            }
                        } catch (e: SecurityException) {
                            // Address can require Bluetooth permission on newer Android versions.
                        }
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> finish()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        continuation.invokeOnCancellation {
            finish()
        }

        try {
            bluetoothAdapter.cancelDiscovery()
            val classicStarted = bluetoothAdapter.startDiscovery()
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bluetoothAdapter.bluetoothLeScanner?.startScan(null, scanSettings, bleCallback)
            if (!classicStarted && bluetoothAdapter.bluetoothLeScanner == null) finish()
        } catch (e: SecurityException) {
            finish()
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val DEFAULT_MISSING_RSSI = -100
        const val WIFI_SCAN_SETTLE_MS = 3_000L
        const val BLUETOOTH_DISCOVERY_TIMEOUT_MS = 10_000L
        const val MAX_WIFI_SIGNALS_PER_SCAN = 18
        const val MAX_BLUETOOTH_SIGNALS_PER_SCAN = 18
    }
}
