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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

//class GPSActivityOLD : ComponentActivity() {
//    companion object {
//        fun newIntent(context: Context) = Intent(context, GPSActivityOLD::class.java)
//    }
//
//    private lateinit var fusedLocationClient: FusedLocationProviderClient
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//
//        setContent {
//            MapScreenOLD(fusedLocationClient)
//        }
//    }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun MapScreenOLD(fusedLocationClient: FusedLocationProviderClient) {
//    val context = LocalContext.current
//    var targetLocation by remember { mutableStateOf<LatLng?>(null) }
//    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
//    var distance by remember { mutableStateOf<Float?>(null) }
//    var hasLocationPermission by remember {
//        mutableStateOf(
//            ContextCompat.checkSelfPermission(
//                context,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED
//        )
//    }
//
//    val cameraPositionState = rememberCameraPositionState()
//    val sharedPreferences = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
//
//    // Ładowanie zapisanego domu
//    LaunchedEffect(Unit) {
//        val latStr = sharedPreferences.getString("home_lat", null)
//        val lngStr = sharedPreferences.getString("home_lng", null)
//        if (latStr != null && lngStr != null) {
//            targetLocation = LatLng(latStr.toDouble(), lngStr.toDouble())
//            cameraPositionState.position = CameraPosition.fromLatLngZoom(targetLocation!!, 15f)
//        }
//    }
//
//    // Callback dla aktualizacji lokalizacji
//    val locationCallback = remember {
//        object : LocationCallback() {
//            override fun onLocationResult(locationResult: LocationResult) {
//                super.onLocationResult(locationResult)
//                locationResult.lastLocation?.let { location ->
//                    currentLocation = LatLng(location.latitude, location.longitude)
//                    targetLocation?.let { home ->
//                        calculateDistanceOLD(currentLocation!!, home) { dist ->
//                            distance = dist
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    // Żądanie uprawnień
//    val locationPermissionLauncher = rememberLauncherForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted ->
//        hasLocationPermission = isGranted
//        if (isGranted) {
//            Toast.makeText(context, "Szukam Twojej lokalizacji...", Toast.LENGTH_SHORT).show()
//        } else {
//            Toast.makeText(context, "Brak uprawnień do lokalizacji", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    // Sprawdź uprawnienia przy starcie
//    LaunchedEffect(Unit) {
//        if (!hasLocationPermission) {
//            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
//        }
//    }
//
//    // Obsługa ciągłych aktualizacji lokalizacji
//    DisposableEffect(hasLocationPermission) {
//        val locationRequest = LocationRequest.create().apply {
//            interval = 10000
//            fastestInterval = 5000
//            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
//        }
//
//        if (hasLocationPermission) {
//            fusedLocationClient.requestLocationUpdates(
//                locationRequest,
//                locationCallback,
//                Looper.getMainLooper()
//            )
//        }
//
//        onDispose {
//            fusedLocationClient.removeLocationUpdates(locationCallback)
//        }
//    }
//
//    Column(modifier = Modifier.fillMaxSize()) {
//        GoogleMap(
//            modifier = Modifier.weight(1f),
//            cameraPositionState = cameraPositionState,
//            properties = MapProperties(isMyLocationEnabled = true),
//            uiSettings = MapUiSettings(zoomControlsEnabled = true),
//            onMapClick = { clickedLocation ->
//                targetLocation = clickedLocation
//                sharedPreferences.edit()
//                    .putString("home_lat", clickedLocation.latitude.toString())
//                    .putString("home_lng", clickedLocation.longitude.toString())
//                    .apply()
//                cameraPositionState.position = CameraPosition.fromLatLngZoom(clickedLocation, 15f)
//                currentLocation?.let { current ->
//                    calculateDistanceOLD(current, clickedLocation) { calculatedDistance ->
//                        distance = calculatedDistance
//                    }
//                }
//            }
//        ) {
//            targetLocation?.let {
//                Marker(
//                    state = MarkerState(position = it),
//                    title = "Twój dom",
//                    snippet = "Zapisana lokalizacja domu"
//                )
//            }
//        }
//
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(16.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            val message = when {
//                targetLocation == null -> "Kliknij na mapę, aby zapisać lokalizację domu"
//                currentLocation == null -> "Szukam aktualnej lokalizacji..."
//                distance == null -> "Obliczam odległość od domu..."
//                distance!! <= 150 -> "Jesteś w domu (${"%.0f".format(distance)}m)"
//                else -> "Jesteś poza domem (${"%.0f".format(distance)}m)"
//            }
//
//            Text(
//                text = message,
//                color = Color.White,
//                modifier = Modifier
//                    .background(MaterialTheme.colorScheme.primary)
//                    .padding(16.dp)
//            )
//        }
//    }
//}
//
//private fun calculateDistanceOLD(
//    start: LatLng,
//    end: LatLng,
//    callback: (Float) -> Unit
//) {
//    val results = FloatArray(1)
//    Location.distanceBetween(
//        start.latitude,
//        start.longitude,
//        end.latitude,
//        end.longitude,
//        results
//    )
//    callback(results[0])
//}