package com.example.wavehome.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wavehome.ActivityTrackingController
import com.example.wavehome.FitApiActivity
import com.example.wavehome.HomeLocationPicker
import com.example.wavehome.HomeApiActivity
import com.example.wavehome.MyRoom
import com.example.wavehome.APP_PREFS_NAME
import com.example.wavehome.SmartHomeSyncWorker
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinishOnboarding: () -> Unit) {
    val context = LocalContext.current
    val appPrefs = remember { context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE) }
    val pageCount = 6
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    var userName by remember { mutableStateOf(appPrefs.getString("user_name", "") ?: "") }
    var homeAddress by remember { mutableStateOf(appPrefs.getString("home_address", "") ?: "") }

    val bgColor = MaterialTheme.colorScheme.background
    val contentColor = MaterialTheme.colorScheme.onBackground
    val inactiveDotColor = MaterialTheme.colorScheme.outlineVariant

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Wstecz", tint = contentColor)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "dotWidth"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) contentColor else inactiveDotColor,
                            label = "dotColor"
                        )
                        Box(modifier = Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
                    }
                }

                IconButton(onClick = {
                    if (pagerState.currentPage < pageCount - 1) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        appPrefs.edit()
                            .putString("user_name", userName.trim())
                            .putString("home_address", homeAddress.trim())
                            .putBoolean("onboarding_completed", true)
                            .apply()
                        ActivityTrackingController.enable(context)
                        SmartHomeSyncWorker.scheduleMinuteLoop(context.applicationContext)
                        onFinishOnboarding()
                    }
                }) {
                    Crossfade(targetState = pagerState.currentPage < pageCount - 1, label = "icon") { isNotLast ->
                        if (isNotLast) Icon(Icons.Default.ArrowForward, null, tint = contentColor)
                        else Icon(Icons.Default.Check, null, tint = contentColor)
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) { page ->
            var isVisible by remember { mutableStateOf(false) }
            LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage == page) isVisible = true }

            androidx.compose.animation.AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(600)) + slideInVertically(initialOffsetY = { 60 })
            ) {
                when (page) {
                    0 -> AnimatedPermissionsPage()
                    1 -> AnimatedInputPage(
                        icon = Icons.Default.Person,
                        title = "Twoje imię",
                        description = "Tylko po to, by aplikacja mogła Cię przywitać na ekranie głównym. Twoje dane nie opuszczają tego telefonu.",
                        hint = "Jak się nazywasz?",
                        value = userName,
                        onValueChange = { userName = it }
                    )
                    2 -> AnimatedHomeLocationPage(
                        homeAddress = homeAddress,
                        onHomeAddressChange = { homeAddress = it }
                    )
                    3 -> AnimatedRoomCalibrationPage()
                    4 -> AnimatedGoogleAuthPage()
                    5 -> AnimatedFinishPage(userName)
                }
            }
        }
    }
}

@Composable
fun AnimatedInputPage(
    icon: ImageVector,
    title: String,
    description: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BreathingIcon(icon = icon, color = contentColor)
        Spacer(modifier = Modifier.height(24.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = contentColor)
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = contentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = contentColor
            )
        )
    }
}

@Composable
fun AnimatedHomeLocationPage(
    homeAddress: String,
    onHomeAddressChange: (String) -> Unit
) {
    val context = LocalContext.current
    val contentColor = MaterialTheme.colorScheme.onBackground
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dom", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = contentColor)
        Text(
            text = "Wpisz opis i kliknij mapę, aby zapisać punkt domu.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = homeAddress,
            onValueChange = onHomeAddressChange,
            placeholder = { Text("Adres lub opis domu", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = contentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                cursorColor = contentColor
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
        ) {
            HomeLocationPicker(
                fusedLocationClient = fusedLocationClient,
                modifier = Modifier.fillMaxSize(),
                compact = true
            )
        }
    }
}

@Composable
fun AnimatedPermissionsPage() {
    val context = LocalContext.current
    val contentColor = MaterialTheme.colorScheme.onBackground

    val permissionsToRequest = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            ActivityTrackingController.enable(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BreathingIcon(icon = Icons.Default.Settings, color = contentColor)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Zgody systemowe", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = contentColor)
        Text(
            text = "Potrzebujemy lokalizacji, aktywności, Bluetooth oraz powiadomień do zbierania kontekstu w tle.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        OutlinedButton(
            onClick = { permissionLauncher.launch(permissionsToRequest) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
        ) {
            Text("Nadaj uprawnienia", modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
fun AnimatedRoomCalibrationPage() {
    val context = LocalContext.current
    val contentColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BreathingIcon(icon = Icons.Default.Wifi, color = contentColor)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Pomieszczenia", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = contentColor)
        Text(
            text = "Zeskanuj WiFi i Bluetooth w każdym pokoju. Te odciski są później używane przez serwis tła do zapisu aktualnego pomieszczenia.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, MyRoom::class.java)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
        ) {
            Text("Kalibruj pokoje", modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
fun AnimatedGoogleAuthPage() {
    val context = LocalContext.current
    val contentColor = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BreathingIcon(
            icon = Icons.Default.AccountCircle,
            color = contentColor
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Google",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )

        Text(
            text = "Połącz Google Home dla urządzeń i Google Fit dla snu. Po zakończeniu konfiguracji WaveHome zacznie zapisywać pełny kontekst.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        OutlinedButton(
            onClick = { context.startActivity(HomeApiActivity.newIntent(context)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
        ) {
            Text("Połącz Google Home", modifier = Modifier.padding(vertical = 12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { context.startActivity(Intent(context, FitApiActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant))
        ) {
            Text("Połącz Google Fit", modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
fun AnimatedFinishPage(userName: String) {
    val contentColor = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BreathingIcon(icon = Icons.Default.CheckCircle, color = contentColor, size = 120)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (userName.isBlank()) "Wszystko gotowe!" else "Gotowe, $userName!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Twój inteligentny dom WaveHome jest teraz gotowy do pracy.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun BreathingIcon(icon: ImageVector, color: Color, size: Int = 100) {
    val infiniteTransition = rememberInfiniteTransition(label = "b")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "s"
    )
    Icon(
        imageVector = icon, contentDescription = null,
        modifier = Modifier.size(size.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        tint = color
    )
}
