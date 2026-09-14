package com.example.wavehome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavehome.ui.onboarding.OnboardingScreen
import com.example.wavehome.ui.theme.WaveHomeTheme


enum class ActivityStatus(val indicatorColor: Color, val description: String) {
    WORKING(indicatorColor = Color(0xFF4CAF50), description = "Działa"), // Zielona kropka
    IN_PROGRESS(indicatorColor = Color(0xFFFFC107), description = "W trakcie rozwoju"), // Żółta kropka
    NOT_STARTED(indicatorColor = Color.Gray, description = "Nieruszone") // Szara kropka
}

data class ActivityButtonInfo(
    val label: String,
    val targetActivity: Class<out ComponentActivity>,
    val status: ActivityStatus
)

class MainActivity : ComponentActivity() {

    private val activityButtons = listOf(
        ActivityButtonInfo("MainTheme", NewMainActivity::class.java, ActivityStatus.NOT_STARTED),
        ActivityButtonInfo("GPS", GPSActivity::class.java, ActivityStatus.WORKING),
        //ActivityButtonInfo("Sensors TEST", SensorsActivity::class.java, ActivityStatus.WORKING),
        ActivityButtonInfo("User Activity", UserActivityActivity::class.java, ActivityStatus.IN_PROGRESS),
        ActivityButtonInfo("Weather", Weather::class.java, ActivityStatus.WORKING),
        ActivityButtonInfo("OAuth", OAuthActivity::class.java, ActivityStatus.WORKING),
        ActivityButtonInfo("Home Api", HomeApiActivity::class.java, ActivityStatus.WORKING),
        ActivityButtonInfo("Test ML", TestMLActivity::class.java, ActivityStatus.IN_PROGRESS),
        ActivityButtonInfo("Test Background Tasks", TestBackgroundTasksActivity::class.java, ActivityStatus.WORKING),
        ActivityButtonInfo("Google Fit API", FitApiActivity::class.java, ActivityStatus.WORKING),
        ActivityButtonInfo("MyRoom", MyRoom::class.java, ActivityStatus.WORKING)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SmartHomeSyncWorker.scheduleMinuteLoop(applicationContext)
        ActivityTrackingController.enable(applicationContext)

        val appPrefs = getSharedPreferences(APP_PREFS_NAME, MODE_PRIVATE)
        if (appPrefs.getBoolean("onboarding_completed", false)) {
            startActivity(NewMainActivity.newIntent(this))
            finish()
            return
        }

        setContent {
            WaveHomeTheme { // Tu poprawnie wywołujemy nasz motyw
                OnboardingScreen(
                    onFinishOnboarding = {
                        startActivity(NewMainActivity.newIntent(this))
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activityButtons: List<ActivityButtonInfo>, onStartOnboarding: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onStartOnboarding,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "URUCHOM KONFIGURACJĘ",
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activityButtons) { buttonInfo ->
                    ActivityButton(
                        buttonInfo = buttonInfo,
                        onClick = { context.launchActivity(buttonInfo.targetActivity) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Legend()
        }
    }
}

@Composable
fun ActivityButton(buttonInfo: ActivityButtonInfo, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = buttonInfo.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Kropka oznaczająca status
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(buttonInfo.status.indicatorColor)
            )
        }
    }
}

@Composable
fun Legend() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "Legenda",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        ActivityStatus.values().forEach { status ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(status.indicatorColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = status.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun Context.launchActivity(activityClass: Class<out ComponentActivity>) {
    startActivity(Intent(this, activityClass))
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val previewButtons = listOf(
        ActivityButtonInfo("Working Feature", ComponentActivity::class.java, ActivityStatus.WORKING),
        ActivityButtonInfo("In Progress Feature", ComponentActivity::class.java, ActivityStatus.IN_PROGRESS),
    )
    WaveHomeTheme {
        MainScreen(activityButtons = previewButtons, onStartOnboarding = {})
    }
}
