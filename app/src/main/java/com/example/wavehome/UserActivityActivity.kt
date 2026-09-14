package com.example.wavehome

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.wavehome.receiver.MoveMoveReceiver
import com.example.wavehome.util.Constants
import com.example.wavehome.util.MoveMoveUtil
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale

class UserActivityActivity : ComponentActivity() {

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var database: ActivityDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityRecognitionClient = ActivityRecognition.getClient(this)
        database = ActivityDatabase.getDatabase(this)

        setContent {
            // Zakładam, że masz zdefiniowany WaveHomeTheme. Jeśli nie, zmień na MaterialTheme
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UserActivityScreen(activityRecognitionClient, database.activityLogDao())
                }
            }
        }
    }
}

@Composable
fun UserActivityScreen(client: ActivityRecognitionClient, dao: ActivityLogDao) {
    val context = LocalContext.current

    // Inicjalizacja pamięci ustawień
    val sharedPreferences = context.getSharedPreferences(APP_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
    val isTrackingSaved = sharedPreferences.getBoolean("is_tracking_enabled", false)

    var trackingEnabled by remember { mutableStateOf(isTrackingSaved) }
    var detectedActivity by remember { mutableStateOf("Oczekiwanie na dane...") }

    // Obserwowanie bazy danych
    val activityLogs by dao.getAllLogsFlow().collectAsState(initial = emptyList())

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            trackingEnabled = true
            sharedPreferences.edit().putBoolean("is_tracking_enabled", true).apply()
            requestActivityUpdates(context, client)
        } else {
            trackingEnabled = false
            Toast.makeText(context, "Uprawnienia są wymagane do działania funkcji", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val activityInfo = intent?.getStringExtra("activity_info")
                if (activityInfo != null) {
                    detectedActivity = activityInfo
                }
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver, IntentFilter("user-activity-update")
        )

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Wykrywanie Aktywności", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Przełącznik
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Śledzenie aktywności:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = trackingEnabled,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        } else {
                            trackingEnabled = true
                            sharedPreferences.edit().putBoolean("is_tracking_enabled", true).apply()
                            requestActivityUpdates(context, client)
                        }
                    } else {
                        trackingEnabled = false
                        sharedPreferences.edit().putBoolean("is_tracking_enabled", false).apply()
                        deregisterActivityUpdates(context, client)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Obecna aktywność
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Aktualnie robisz:", style = MaterialTheme.typography.titleSmall)
                Text(text = detectedActivity, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        // Historia aktywności
        Text(text = "Historia aktywności", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activityLogs) { log ->
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", LocalLocale.current.platformLocale)
                val dateString = dateFormat.format(Date(log.timestamp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buildString {
                                append(log.userStatus ?: log.activityType)
                                log.transitionType?.let { append(" ($it)") }
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = dateString,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Funkcje pomocnicze
// =====================================================================

private fun getActivityTransitionPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MoveMoveReceiver::class.java)

    // POPRAWKA DLA ANDROIDA 12+: Dodanie FLAG_MUTABLE
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    return PendingIntent.getBroadcast(
        context,
        Constants.REQUEST_CODE_INTENT_ACTIVITY_TRANSITION,
        intent,
        flags
    )
}

private fun requestActivityUpdates(context: Context, client: ActivityRecognitionClient) {
    try {
        if (ActivityTrackingController.enable(context)) {
            Toast.makeText(context, "Rozpoczęto śledzenie aktywności", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Błąd podczas rozpoczynania śledzenia", Toast.LENGTH_SHORT).show()
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Brak uprawnień do śledzenia", Toast.LENGTH_SHORT).show()
    }
}

private fun deregisterActivityUpdates(context: Context, client: ActivityRecognitionClient) {
    try {
        ActivityTrackingController.disable(context)
        Toast.makeText(context, "Zakończono śledzenie aktywności", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        // Obsługa braku uprawnień
    }
}
