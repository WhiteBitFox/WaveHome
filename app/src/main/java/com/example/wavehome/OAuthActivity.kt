package com.example.wavehome

import android.app.Activity
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.wavehome.ui.theme.WaveHomeTheme
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import org.json.JSONObject

class OAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WaveHomeTheme {
                OAuthScreen()
            }
        }
    }
}


class OAuth(context: android.content.Context) {
    private val oneTapClient = Identity.getSignInClient(context)
    private val signInRequest =
        com.google.android.gms.auth.api.identity.BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                com.google.android.gms.auth.api.identity.BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId("412770304732-6mt3hgpkn3p31e3dsqgmt7aeqcm0oed0.apps.googleusercontent.com")
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

    fun signIn(launcher: (IntentSenderRequest) -> Unit, onError: (String) -> Unit) {
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                try {
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(result.pendingIntent).build()
                    launcher(intentSenderRequest)
                } catch (e: Exception) {
                    onError("Błąd uruchamiania: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener { e ->
                val errorMessage = when (val apiEx = e as? ApiException) {
                    null -> "Nieznany błąd: ${e.localizedMessage}"
                    else -> when (apiEx.statusCode) {
                        com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> "Anulowano przez użytkownika"
                        com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Błąd sieci"
                        16 -> "Brak pasujących kont"
                        else -> "Błąd: ${apiEx.statusCode} - ${apiEx.message}"
                    }
                }
                onError(errorMessage)
            }
    }

    fun signOut(onSuccess: () -> Unit, onError: (String) -> Unit) {
        oneTapClient.signOut()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                onError("Błąd wylogowania: ${e.localizedMessage}")
            }
    }
}

data class GoogleUser(
    val email: String?,
    val name: String?,
    val pictureUrl: String?
)

fun decodeIdToken(token: String): GoogleUser? {
    return try {
        val parts = token.split('.')
        val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE)
        val payloadJson = String(payloadBytes, Charsets.UTF_8)
        val jsonObject = JSONObject(payloadJson)

        GoogleUser(
            email = jsonObject.optString("email"),
            name = jsonObject.optString("name"),
            pictureUrl = jsonObject.optString("picture")
        )
    } catch (e: Exception) {
        null
    }
}

@Composable
fun OAuthScreen() {
    val context = LocalContext.current
    val oauth = remember { OAuth(context) }
    val sharedPrefs =
        context.getSharedPreferences("WaveHomeAuth", android.content.Context.MODE_PRIVATE)

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<GoogleUser?>(null) }

    LaunchedEffect(Unit) {
        val savedToken = sharedPrefs.getString("SAVED_GOOGLE_TOKEN", null)
        if (savedToken != null) {
            user = decodeIdToken(savedToken)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        isLoading = false
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val credential = Identity.getSignInClient(context)
                    .getSignInCredentialFromIntent(result.data)

                credential.googleIdToken?.let { token ->
                    user = decodeIdToken(token)
                    error = null
                    sharedPrefs.edit().putString("SAVED_GOOGLE_TOKEN", token).apply()
                } ?: run { error = "Brak tokena w odpowiedzi" }
            } catch (e: ApiException) {
                error = "Błąd API: ${e.statusCode} - ${e.message}"
            } catch (e: Exception) {
                error = "Nieoczekiwany błąd: ${e.localizedMessage}"
            }
        } else {
            error = "Anulowano logowanie"
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Łączenie z Google...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (user == null) {
                // --- WIDOK WYLOGOWANY ---
                AnimatedPulsingIcon(
                    icon = Icons.Rounded.CloudSync,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Twoje konto Google",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Zaloguj się swoim kontem Google, aby odblokować pełną kontrolę nad inteligentnym domem.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = {
                        isLoading = true
                        error = null
                        oauth.signIn(
                            launcher = { launcher.launch(it) },
                            onError = {
                                error = it
                                isLoading = false
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Kontynuuj z Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // --- WIDOK ZALOGOWANY ---
                Text(
                    text = "Twój Profil",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 24.dp)
                )

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = user?.pictureUrl,
                            contentDescription = "Zdjęcie profilowe",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = user?.name ?: "Użytkownik",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = user?.email ?: "Brak adresu e-mail",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Zgrabna plakietka "Połączono"
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Połączono z chmurą",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = {
                        oauth.signOut(
                            onSuccess = {
                                user = null
                                sharedPrefs.edit().remove("SAVED_GOOGLE_TOKEN").apply()
                            },
                            onError = { error = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            MaterialTheme.colorScheme.error.copy(
                                alpha = 0.3f
                            )
                        )
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Wyloguj się", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            error?.let {
                Text(
                    text = "Błąd: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AnimatedPulsingIcon(icon: ImageVector, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        // Delikatne tło (poświata)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = color
        )
    }
}