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
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wavehome.ui.theme.WaveHomeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROOM_SCAN_DURATION_MS = 10_000L
private const val ROOM_FINGERPRINT_SAMPLES = 5
private const val ROOM_FINGERPRINT_PAUSE_MS = 2_000L

class MyRoom : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Wymagane uprawnienia nie zostały przyznane!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionsToRequest = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            WaveHomeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    IndoorPositioningApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndoorPositioningApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wifiManager = remember { context.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager? }
    val bluetoothAdapter = remember { bluetoothManager?.adapter }

    val wifiList = remember { mutableStateListOf<ScanResult>() }
    val btList = remember { mutableStateListOf<BluetoothDeviceInfo>() }

    var isScanning by remember { mutableStateOf(false) }
    var isCapturingFingerprints by remember { mutableStateOf(false) }
    var roomNameInput by remember { mutableStateOf("") }
    var showClearCalibrationDialog by remember { mutableStateOf(false) }
    var fingerprintProgress by remember { mutableStateOf("") }

    fun upsertBluetoothDevice(device: BluetoothDevice, rssi: Short?) {
        try {
            val existingIndex = btList.indexOfFirst { info -> info.device.address == device.address }
            if (existingIndex != -1) {
                btList[existingIndex] = btList[existingIndex].copy(rssi = rssi)
            } else {
                btList.add(BluetoothDeviceInfo(device, rssi))
            }
        } catch (e: SecurityException) {
            // Address access can require Bluetooth permission on newer Android versions.
        }
    }

    val bleScanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: BleScanResult?) {
                result?.device?.let { device ->
                    scope.launch(Dispatchers.Main) {
                        upsertBluetoothDevice(device, result.rssi.toShort())
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<BleScanResult>?) {
                results.orEmpty().forEach { result ->
                    result.device?.let { device ->
                        scope.launch(Dispatchers.Main) {
                            upsertBluetoothDevice(device, result.rssi.toShort())
                        }
                    }
                }
            }
        }
    }

    val combinedReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                        try {
                            if (ContextCompat.checkSelfPermission(context!!, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                val results = wifiManager.scanResults.sortedByDescending { it.level }.distinctBy { it.BSSID }
                                wifiList.clear()
                                wifiList.addAll(results)
                            }
                        } catch (e: SecurityException) { }
                    }

                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        device?.let {
                            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)
                            upsertBluetoothDevice(it, rssi)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        ContextCompat.registerReceiver(context, combinedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
                try {
                    bluetoothAdapter?.cancelDiscovery()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
                    }
                    context.unregisterReceiver(combinedReceiver)
                } catch (e: Exception) { }
            }
    }

    fun startCombinedScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        isScanning = true
        wifiList.clear()
        btList.clear()

        wifiManager.startScan()

        scope.launch(Dispatchers.IO) {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    val scanSettings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    withContext(Dispatchers.Main) {
                        bluetoothAdapter?.startDiscovery()
                        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, bleScanCallback)
                    }
                }
            } catch (e: SecurityException) {}

            delay(ROOM_SCAN_DURATION_MS)

            withContext(Dispatchers.Main) {
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        val results = wifiManager.scanResults.sortedByDescending { it.level }.distinctBy { it.BSSID }
                        wifiList.clear()
                        wifiList.addAll(results)
                    }
                } catch (e: SecurityException) {}
                try { bluetoothAdapter?.cancelDiscovery() } catch (e: SecurityException) {}
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
                    }
                } catch (e: SecurityException) {}
                isScanning = false
            }
        }
    }

    fun captureFingerprintSeries() {
        if (roomNameInput.isBlank() || isCapturingFingerprints) return

        isCapturingFingerprints = true
        scope.launch {
            var savedCount = 0
            repeat(ROOM_FINGERPRINT_SAMPLES) { index ->
                fingerprintProgress = "Próbka ${index + 1}/$ROOM_FINGERPRINT_SAMPLES"
                startCombinedScan()
                delay(ROOM_SCAN_DURATION_MS + 1_000L)
                if (wifiList.isNotEmpty() || btList.isNotEmpty()) {
                    viewModel.saveRoomFingerprint(roomNameInput, wifiList, btList)
                    savedCount++
                }
                if (index < ROOM_FINGERPRINT_SAMPLES - 1) delay(ROOM_FINGERPRINT_PAUSE_MS)
            }
            Toast.makeText(context, "Zapisano $savedCount odciski: $roomNameInput", Toast.LENGTH_SHORT).show()
            roomNameInput = ""
            fingerprintProgress = ""
            isCapturingFingerprints = false
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            startCombinedScan()
            delay(270_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Radar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    Text("WiFi: ${wifiList.size}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Bluetooth: ${btList.size}", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { startCombinedScan() },
                    enabled = !isScanning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isScanning) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Skanuj otoczenie", modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Kalibracja Pokoju", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start), color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = roomNameInput,
            onValueChange = { roomNameInput = it },
            placeholder = { Text("np. Salon", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                captureFingerprintSeries()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            enabled = roomNameInput.isNotBlank() && !isCapturingFingerprints
        ) {
            if (isCapturingFingerprints) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text("Zapisz dokładne odciski pokoju", modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        if (fingerprintProgress.isNotBlank()) {
            Text(
                fingerprintProgress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (viewModel.calibratedRooms.isNotEmpty()) {
            val roomCounts = viewModel.calibratedRooms
                .groupingBy { it.roomName }
                .eachCount()
            Text(
                "Zapisane: ${roomCounts.entries.joinToString { "${it.key} (${it.value})" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showClearCalibrationDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                enabled = !isCapturingFingerprints
            ) {
                Text("Usuń wszystkie odciski pokoi", modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        if (showClearCalibrationDialog) {
            AlertDialog(
                onDismissRequest = { showClearCalibrationDialog = false },
                title = { Text("Usunąć kalibrację?") },
                text = {
                    Text(
                        "Zostaną usunięte wszystkie odciski WiFi/Bluetooth oraz historia detekcji pomieszczeń. Po tej operacji trzeba ponownie zeskanować pokoje."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearRoomCalibration()
                            showClearCalibrationDialog = false
                            Toast.makeText(context, "Usunięto odciski pokoi", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Usuń")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showClearCalibrationDialog = false }) {
                        Text("Anuluj")
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Text("Lokalizacja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start), color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.locateMe(wifiList, btList) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            enabled = viewModel.calibratedRooms.isNotEmpty() && (wifiList.isNotEmpty() || btList.isNotEmpty())
        ) {
            Text("Gdzie jestem?", modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Wykryto Cię w:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = viewModel.currentRoomEstimate.ifEmpty { "..." },
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black
        )
    }
}
