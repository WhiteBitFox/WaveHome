package com.example.wavehome

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wavehome.homeapi.HomeController
import com.example.wavehome.ui.onboarding.OnboardingScreen
import com.example.wavehome.ui.theme.WaveHomeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewMainActivity : ComponentActivity() {
    companion object {
        fun newIntent(context: Context) = Intent(context, NewMainActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(applicationContext)
        val homeController = HomeController.getInstance(applicationContext)

        setContent {
            WaveHomeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WaveHomeApp(db = db, homeController = homeController)
                }
            }
        }
    }
}

private data class MainDashboardState(
    val latestLog: TimeLog? = null,
    val latestSleep: SleepSnapshot? = null,
    val deviceStates: List<SmartHomeDeviceSnapshot> = emptyList(),
    val lastUpdatedAt: Long? = null,
    val homeControlEnabled: Boolean = true
)

private enum class MainTab(
    val label: String,
    val icon: ImageVector
) {
    HOME("Dom", Icons.Default.Home),
    DEVICES("Urządzenia", Icons.Default.Build),
    AUTOMATION("Automatyka", Icons.Default.PlayArrow),
    SETTINGS("Ustawienia", Icons.Default.Settings)
}

@Composable
private fun WaveHomeApp(
    db: AppDatabase,
    homeController: HomeController
) {
    val context = LocalContext.current
    val appPrefs = remember { context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE) }
    val settingsPrefs = remember { context.getSharedPreferences(APP_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showOnboarding by remember {
        mutableStateOf(!appPrefs.getBoolean("onboarding_completed", false))
    }
    var dashboardState by remember { mutableStateOf(MainDashboardState()) }
    var sleepSyncEnabled by remember {
        mutableStateOf(settingsPrefs.getBoolean(PREF_SYNC_SLEEP_WITH_GOOGLE_FIT, true))
    }

    LaunchedEffect(showOnboarding) {
        if (showOnboarding) return@LaunchedEffect

        while (true) {
            dashboardState = loadDashboardState(context, db, homeController, settingsPrefs)
            delay(30_000L)
        }
    }

    if (showOnboarding) {
        OnboardingScreen(onFinishOnboarding = {
            showOnboarding = false
        })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                MainTab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (MainTab.values()[selectedTab]) {
            MainTab.HOME -> HomeDashboardPage(
                padding = padding,
                state = dashboardState,
                onRefresh = {
                    dashboardState = MainDashboardState(
                        latestLog = dashboardState.latestLog,
                        latestSleep = dashboardState.latestSleep,
                        deviceStates = dashboardState.deviceStates,
                        lastUpdatedAt = System.currentTimeMillis(),
                        homeControlEnabled = dashboardState.homeControlEnabled
                    )
                },
                reload = {
                    dashboardState = loadDashboardState(
                        context = context,
                        db = db,
                        homeController = homeController,
                        settingsPrefs = settingsPrefs,
                        collectFresh = true
                    )
                }
            )
            MainTab.DEVICES -> DevicesPage(
                padding = padding,
                devices = dashboardState.deviceStates,
                db = db,
                homeController = homeController,
                onReload = {
                    dashboardState = loadDashboardState(context, db, homeController, settingsPrefs)
                }
            )
            MainTab.AUTOMATION -> AutomationPage(
                padding = padding,
                latestLog = dashboardState.latestLog,
                db = db,
                homeControlEnabled = dashboardState.homeControlEnabled,
                onHomeControlChange = { enabled ->
                    settingsPrefs.edit().putBoolean(PREF_HOME_CONTROL_ENABLED, enabled).apply()
                    dashboardState = dashboardState.copy(homeControlEnabled = enabled)
                }
            )
            MainTab.SETTINGS -> SettingsPage(
                padding = padding,
                sleepSyncEnabled = sleepSyncEnabled,
                onSleepSyncChange = { enabled ->
                    settingsPrefs.edit()
                        .putBoolean(PREF_SYNC_SLEEP_WITH_GOOGLE_FIT, enabled)
                        .apply()
                    sleepSyncEnabled = enabled
                    if (!enabled) {
                        dashboardState = dashboardState.copy(latestSleep = null)
                    }
                },
                onOpenHome = { context.startActivity(HomeApiActivity.newIntent(context)) },
                onOpenRooms = { context.startActivity(Intent(context, MyRoom::class.java)) },
                onOpenLocation = { context.startActivity(GPSActivity.newIntent(context)) },
                onOpenHistory = { context.startActivity(Intent(context, TestBackgroundTasksActivity::class.java)) },
                onRestartSetup = { showOnboarding = true }
            )
        }
    }
}

@Composable
private fun HomeDashboardPage(
    padding: PaddingValues,
    state: MainDashboardState,
    onRefresh: () -> Unit,
    reload: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestLog = state.latestLog
    val latestSleep = state.latestSleep ?: latestLog?.toSleepSnapshot()
    val activeDevices = state.deviceStates.count { it.isOn == true }
    val totalDevices = state.deviceStates.count { it.supportsOnOff }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatusBand(
                latestLog = latestLog,
                activeDevices = activeDevices,
                totalDevices = totalDevices,
                homeControlEnabled = state.homeControlEnabled,
                onRefresh = {
                    onRefresh()
                    scope.launch { reload() }
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Pomieszczenie",
                    value = latestLog?.roomName ?: "Nieznane",
                    detail = latestLog?.userStatus ?: latestLog?.activityType ?: "Brak aktywności",
                    icon = Icons.Default.LocationOn,
                    color = Color(0xFFE6F4EA),
                    contentColor = Color(0xFF174EA6)
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Urządzenia",
                    value = "$activeDevices/$totalDevices",
                    detail = "włączone",
                    icon = Icons.Default.Star,
                    color = Color(0xFFFFF3E0),
                    contentColor = Color(0xFF8A4B00)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Temperatura",
                    value = latestLog?.temperature?.let { "%.1f°C".format(it) } ?: "--",
                    detail = latestLog?.humidity?.let { "$it% wilgotności" } ?: "brak pogody",
                    icon = Icons.Default.Info,
                    color = Color(0xFFE3F2FD),
                    contentColor = Color(0xFF0B57D0)
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "Sen",
                    value = latestSleep?.minutes?.let { "${it / 60}h ${it % 60}m" } ?: "--",
                    detail = latestSleep?.range ?: "Google Fit",
                    icon = Icons.Default.Favorite,
                    color = Color(0xFFFCE8E6),
                    contentColor = Color(0xFFB3261E)
                )
            }
        }

        item {
            SectionTitle("Ostatnia decyzja")
            PredictionPanel(latestLog)
        }

        item {
            SectionTitle("Kontekst zapisany do modelu")
            ContextRows(latestLog)
        }
    }
}

@Composable
private fun StatusBand(
    latestLog: TimeLog?,
    activeDevices: Int,
    totalDevices: Int,
    homeControlEnabled: Boolean,
    onRefresh: () -> Unit
) {
    val isHome = latestLog?.isHome == true
    val statusColor = if (isHome) Color(0xFF137333) else Color(0xFFB3261E)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isHome) "Dom aktywny" else "Poza domem",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Odśwież dane")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${latestLog?.roomName ?: "Brak pomieszczenia"} · ${latestLog?.userStatus ?: "status nieznany"} · $activeDevices z $totalDevices urządzeń działa",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!homeControlEnabled) {
                Text(
                    text = "Automatyczne sterowanie jest zatrzymane, zbieranie danych trwa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = latestLog?.timestamp?.let { "Ostatni zapis: ${formatTime(it)}" } ?: "Brak zapisanych pomiarów",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier,
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color
) {
    Card(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PredictionPanel(latestLog: TimeLog?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Klasa wyjściowa",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = latestLog?.predictedOutputClass?.toString() ?: "Brak",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(latestLog?.predictionSource ?: "BRAK MODELU") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (latestLog?.predictionSource == PredictionSource.USER_OVERRIDE.name) {
                                Icons.Default.Warning
                            } else {
                                Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextRows(latestLog: TimeLog?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow("Obecność", if (latestLog?.isHome == true) "W domu" else "Poza domem")
            InfoRow("Pomieszczenie", latestLog?.roomName ?: "Brak")
            InfoRow("Status użytkownika", latestLog?.userStatus ?: "Brak")
            InfoRow("Czynność", latestLog?.activityType ?: "Brak")
            InfoRow("Spał", if (latestLog?.sleptLastNight == true) "Tak" else "Nie")
            InfoRow("Teraz śpi", if (latestLog?.isSleeping == true) "Tak" else "Nie")
            InfoRow("Pogoda", latestLog?.temperature?.let { "%.1f°C".format(it) } ?: "Brak")
            InfoRow("Wektor ML", latestLog?.mlInputVector?.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) } ?: "Brak")
        }
    }
}

@Composable
private fun DevicesPage(
    padding: PaddingValues,
    devices: List<SmartHomeDeviceSnapshot>,
    db: AppDatabase,
    homeController: HomeController,
    onReload: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Urządzenia Google Home")
        }

        if (devices.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Build,
                    title = "Brak urządzeń",
                    subtitle = "Połącz Google Home w ustawieniach."
                )
            }
        } else {
            items(devices, key = { it.deviceId }) { device ->
                DeviceControlRow(
                    device = device,
                    onToggle = { desiredOn ->
                        scope.launch {
                            val success = homeController.setDevicePower(device.deviceId, desiredOn)
                            if (success) {
                                saveManualDeviceOverride(db, device, desiredOn)
                                onReload()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DeviceControlRow(
    device: SmartHomeDeviceSnapshot,
    onToggle: (Boolean) -> Unit
) {
    val typeLabel = when (device.type) {
        SmartDeviceType.LIGHT -> "Światło"
        SmartDeviceType.PLUG -> "Gniazdko"
        SmartDeviceType.TV -> "Telewizor"
        SmartDeviceType.UNKNOWN -> "Urządzenie"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$typeLabel · ${if (device.isOn == true) "Włączone" else "Wyłączone"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = device.isOn == true,
                enabled = device.supportsOnOff,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun AutomationPage(
    padding: PaddingValues,
    latestLog: TimeLog?,
    db: AppDatabase,
    homeControlEnabled: Boolean,
    onHomeControlChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelStore = remember { LocalSequenceModelStore(context) }
    var trainingSummary by remember { mutableStateOf<LocalTrainingSummary?>(null) }
    var trainingError by remember { mutableStateOf<String?>(null) }
    var isTraining by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        trainingSummary = modelStore.loadSummary()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionTitle("Sterowanie domem")
            HomeControlPauseCard(
                enabled = homeControlEnabled,
                onChange = onHomeControlChange
            )
        }
        item {
            SectionTitle("Uczenie lokalne")
            TrainingPanel(
                summary = trainingSummary,
                error = trainingError,
                isTraining = isTraining,
                onTrain = {
                    scope.launch {
                        isTraining = true
                        trainingError = null
                        val result = LocalSequenceTrainer(
                            db = db,
                            modelStore = modelStore
                        ).train()
                        result
                            .onSuccess { trainingSummary = it }
                            .onFailure { trainingError = it.message ?: "Nie udało się wytrenować modelu." }
                        isTraining = false
                    }
                }
            )
        }
        item {
            SectionTitle("Model LSTM")
            AutomationStatusCard(
                title = "Ostatnia predykcja",
                value = latestLog?.predictedOutputClass?.let { "Klasa $it" } ?: "Brak danych",
                detail = latestLog?.predictionSource ?: "Oczekiwanie na pierwszy cykl"
            )
        }
        item {
            AutomationStatusCard(
                title = "Dane wejściowe",
                value = latestLog?.mlInputVector?.size?.let { "$it cech" } ?: "Brak",
                detail = latestLog?.timestamp?.let { "Zapis: ${formatTime(it)}" } ?: "Serwis jeszcze nie zapisał kontekstu"
            )
        }
        item {
            AutomationStatusCard(
                title = "Ochrona ręcznych decyzji",
                value = "30 min",
                detail = "Ręczne przełączenia blokują sprzeczną decyzję modelu"
            )
        }
    }
}

@Composable
private fun HomeControlPauseCard(
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Automatyczne sterowanie",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (enabled) {
                        "Model może sterować urządzeniami"
                    } else {
                        "Sterowanie zatrzymane, dane nadal są zbierane"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onChange
            )
        }
    }
}

@Composable
private fun TrainingPanel(
    summary: LocalTrainingSummary?,
    error: String?,
    isTraining: Boolean,
    onTrain: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Personalizacja z historii",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        summary?.let {
                            "${it.sampleCount} próbek · ${it.classCount} klas · ${(it.accuracy * 100).toInt()}% dopasowania"
                        } ?: "Brak lokalnie wytrenowanego modelu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onTrain,
                    enabled = !isTraining,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTraining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Ucz")
                    }
                }
            }

            summary?.let {
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow("Ostatni trening", formatTime(it.trainedAt))
                InfoRow("Rozmiar wejścia", "${it.featureCount} wartości")
            }

            if (!error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AutomationStatusCard(
    title: String,
    value: String,
    detail: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    sleepSyncEnabled: Boolean,
    onSleepSyncChange: (Boolean) -> Unit,
    onOpenHome: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenHistory: () -> Unit,
    onRestartSetup: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Konfiguracja")
            SettingsSwitch(
                title = "Synchronizacja snu",
                subtitle = "Google Fit API",
                checked = sleepSyncEnabled,
                onCheckedChange = onSleepSyncChange
            )
            SettingsButton("Google Home", "Urządzenia, Matter, TV", Icons.Default.Home, onOpenHome)
            SettingsButton("Pomieszczenia", "Fingerprint WiFi i Bluetooth", Icons.Default.LocationOn, onOpenRooms)
            SettingsButton("Położenie domu", "GPS i strefa domu", Icons.Default.Info, onOpenLocation)
            SettingsButton("Historia danych", "TimeLog, sen, aktywność, urządzenia", Icons.Default.Star, onOpenHistory)
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRestartSetup,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Uruchom konfigurację")
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.55f)
        )
    }
    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.45f))
}

private suspend fun loadDashboardState(
    context: Context,
    db: AppDatabase,
    homeController: HomeController,
    settingsPrefs: SharedPreferences,
    collectFresh: Boolean = false
): MainDashboardState {
    return try {
        val currentLatestLog = db.timeLogDao().getLatestEntry()
        val recordedCycle = if (collectFresh || currentLatestLog == null) {
            SmartHomeDataRecorder(context, db, homeController)
                .collectAndSave(applyHomeControl = false)
        } else {
            null
        }

        val latestLog = recordedCycle?.timeLog ?: db.timeLogDao().getLatestEntry()
        val latestSleep = SleepDataSource(context).getLastSleepSnapshot() ?: latestLog?.toSleepSnapshot()
        val liveDevices = recordedCycle?.deviceStates ?: homeController.getCurrentDeviceSnapshots()
        val fallbackDevices = latestLog?.let { log ->
            db.timeLogDao().getDeviceStatesForTimeLog(log.id).map { it.toSnapshot() }
        }.orEmpty()

        MainDashboardState(
            latestLog = latestLog,
            latestSleep = latestSleep,
            deviceStates = liveDevices.ifEmpty { fallbackDevices },
            lastUpdatedAt = System.currentTimeMillis(),
            homeControlEnabled = settingsPrefs.getBoolean(PREF_HOME_CONTROL_ENABLED, true)
        )
    } catch (e: Exception) {
        Log.e("NewMainActivity", "Nie udało się odświeżyć dashboardu", e)
        val latestLog = runCatching { db.timeLogDao().getLatestEntry() }.getOrNull()
        MainDashboardState(
            latestLog = latestLog,
            latestSleep = latestLog?.toSleepSnapshot(),
            deviceStates = latestLog?.let { log ->
                runCatching { db.timeLogDao().getDeviceStatesForTimeLog(log.id).map { it.toSnapshot() } }
                    .getOrDefault(emptyList())
            }.orEmpty(),
            lastUpdatedAt = System.currentTimeMillis(),
            homeControlEnabled = settingsPrefs.getBoolean(PREF_HOME_CONTROL_ENABLED, true)
        )
    }
}

private suspend fun saveManualDeviceOverride(
    db: AppDatabase,
    device: SmartHomeDeviceSnapshot,
    desiredOn: Boolean
) {
    val now = System.currentTimeMillis()
    db.overrideDao().insert(
        UserOverride(
            sensorSnapshot = FloatArray(SENSOR_FEATURE_COUNT),
            correctOutput = 0,
            timestamp = now,
            kind = OVERRIDE_KIND_MANUAL_DEVICE,
            deviceId = device.deviceId,
            deviceType = device.type.name,
            desiredOn = desiredOn,
            expiresAt = now + MANUAL_DEVICE_OVERRIDE_TTL_MS
        )
    )
}

private fun TimeLogDeviceState.toSnapshot(): SmartHomeDeviceSnapshot {
    return SmartHomeDeviceSnapshot(
        deviceId = deviceId,
        name = deviceName,
        type = runCatching { SmartDeviceType.valueOf(deviceType) }.getOrDefault(SmartDeviceType.UNKNOWN),
        isOn = isOn,
        supportsOnOff = supportsOnOff,
        capturedAt = capturedAt
    )
}

private fun TimeLog.toSleepSnapshot(): SleepSnapshot? {
    val minutes = sleepMinutesLastNight ?: return null
    return SleepSnapshot(
        minutes = minutes,
        range = sleepRange,
        slept = sleptLastNight == true,
        isSleepingNow = isSleeping == true
    )
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
