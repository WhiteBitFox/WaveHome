package com.example.wavehome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wavehome.SmartDeviceType
import com.example.wavehome.SmartHomeDeviceSnapshot
import com.example.wavehome.homeapi.HomeViewModel
import com.example.wavehome.ui.theme.WaveHomeTheme

class HomeApiActivity : ComponentActivity() {
    companion object {
        fun newIntent(context: Context) = Intent(context, HomeApiActivity::class.java)
    }

    private lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(applicationContext) as T
                }
            }
        )[HomeViewModel::class.java]

        viewModel.initHomePermissions(this)

        setContent {
            WaveHomeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun HomeDashboardScreen(viewModel: HomeViewModel) {
    val devices by viewModel.deviceStates.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "Twój Smart Home",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Łączenie z Google Home...", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(devices) { device ->
                    DeviceCard(device = device, onToggle = { viewModel.toggleDevice(it) })
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: SmartHomeDeviceSnapshot, onToggle: (SmartHomeDeviceSnapshot) -> Unit) {
    val isOn = device.isOn == true
    val supportsOnOff = device.supportsOnOff
    val typeLabel = when (device.type) {
        SmartDeviceType.LIGHT -> "Światło"
        SmartDeviceType.PLUG -> "Gniazdko"
        SmartDeviceType.TV -> "Telewizor"
        SmartDeviceType.UNKNOWN -> "Nieznany typ"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if(isOn) 2.dp else 0.dp),
        border = if(!isOn) CardDefaults.outlinedCardBorder().copy(width = 1.dp, brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = device.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = if(isOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (supportsOnOff) "$typeLabel · ${if (isOn) "Włączone" else "Wyłączone"}" else "$typeLabel · Tylko odczyt",
                    color = if(isOn) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }

            if (supportsOnOff) {
                Switch(
                    checked = isOn,
                    onCheckedChange = { onToggle(device) }
                )
            }
        }
    }
}
