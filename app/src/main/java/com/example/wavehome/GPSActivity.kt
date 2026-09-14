package com.example.wavehome

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.wavehome.ui.theme.WaveHomeTheme
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

class GPSActivity : ComponentActivity() {
    companion object {
        fun newIntent(context: Context) = Intent(context, GPSActivity::class.java)
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            WaveHomeTheme {
                MapScreen(fusedLocationClient)
            }
        }
    }
}

@Composable
fun MapScreen(fusedLocationClient: FusedLocationProviderClient) {
    HomeLocationPicker(fusedLocationClient = fusedLocationClient)
}

@Composable
fun HomeLocationPicker(
    fusedLocationClient: FusedLocationProviderClient,
    modifier: Modifier = Modifier.fillMaxSize(),
    compact: Boolean = false
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE) }

    var targetLocation by remember { mutableStateOf<LatLng?>(null) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var distance by remember { mutableStateOf<Float?>(null) }

    // Flaga do jednorazowego wycentrowania mapy na użytkowniku
    var hasCentredOnUser by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPositionState = rememberCameraPositionState()

    // 1. Ładowanie zapisanego domu
    LaunchedEffect(Unit) {
        val savedLat = sharedPreferences.getString("home_lat", null)?.toDoubleOrNull()
        val savedLng = sharedPreferences.getString("home_lng", null)?.toDoubleOrNull()
        if (savedLat != null && savedLng != null) {
            targetLocation = LatLng(savedLat, savedLng)
        }
    }

    // 2. KLUCZOWA ZMIANA: Płynne animowanie kamery na użytkowniku po pobraniu pozycji
    LaunchedEffect(currentLocation) {
        if (currentLocation != null && !hasCentredOnUser) {
            // Zamiast nagłego przeskoku (z afryki do domu), używamy animate
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f),
                durationMs = 1500 // czas animacji na 1.5 sekundy
            )
            hasCentredOnUser = true
        }
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    currentLocation = LatLng(location.latitude, location.longitude)
                    targetLocation?.let { home ->
                        calculateDistance(currentLocation!!, home) { dist ->
                            distance = dist
                            val isHome = dist <= 150
                            sharedPreferences.edit().putBoolean("is_home", isHome).apply()
                        }
                    }
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Szukam Twojej lokalizacji...", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(hasLocationPermission) {
        // Zaktualizowany LocationRequest (zgodnie z nowymi standardami API)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        if (hasLocationPermission) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                // Obsługa braku uprawnień w trakcie działania
                Toast.makeText(context, "Brak uprawnień do lokalizacji", Toast.LENGTH_SHORT).show()
            }
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            onMapClick = { clickedLocation ->
                targetLocation = clickedLocation
                sharedPreferences.edit()
                    .putString("home_lat", clickedLocation.latitude.toString())
                    .putString("home_lng", clickedLocation.longitude.toString())
                    .apply()

                coroutineScope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(clickedLocation, 15f),
                        durationMs = 800
                    )
                }

                currentLocation?.let { current ->
                    calculateDistance(current, clickedLocation) { calculatedDistance ->
                        distance = calculatedDistance
                    }
                }
            }
        ) {
            targetLocation?.let {
                Marker(state = MarkerState(position = it), title = "Twój dom")
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 12.dp else 24.dp)
            ) {
                Text("Zapisany dom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                targetLocation?.let {
                    Text("Lat: ${"%.6f".format(it.latitude)} | Lng: ${"%.6f".format(it.longitude)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } ?: Text("Kliknij na mapę, aby zapisać lokalizację", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(16.dp))

                if (!compact) {
                    Text("Aktualna pozycja", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    currentLocation?.let {
                        Text("Lat: ${"%.6f".format(it.latitude)} | Lng: ${"%.6f".format(it.longitude)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } ?: Text("Szukam...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                when {
                    targetLocation == null -> Text("Najpierw zapisz dom", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    currentLocation == null -> Text("Oczekiwanie na GPS...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    distance == null -> Text("Obliczam...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(
                        text = "${"%.0f".format(distance)} m • " + if (distance!! <= 100) "W domu" else "Poza domem",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (distance!! <= 100) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun calculateDistance(
    start: LatLng,
    end: LatLng,
    callback: (Float) -> Unit
) {
    val results = FloatArray(1)
    Location.distanceBetween(
        start.latitude,
        start.longitude,
        end.latitude,
        end.longitude,
        results
    )
    callback(results[0])
}
