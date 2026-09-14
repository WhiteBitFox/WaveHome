package com.example.wavehome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TestMLActivity : ComponentActivity() {
    companion object {
        fun newIntent(context: Context) = Intent(context, TestMLActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Zmienne stanu Compose (gdy się zmienią, ekran sam się odświeży)
            var currentStatus by remember { mutableStateOf("Oczekiwanie na start...") }
            var finalResults by remember { mutableStateOf("") }

            // Uruchomienie w tle zaraz po załadowaniu ekranu
            LaunchedEffect(Unit) {
                // withContext(Dispatchers.IO) przenosi ciężkie obliczenia z dala od wątku interfejsu
                val result = withContext(Dispatchers.IO) {
                    val benchmark = SmartHomeMLBenchmark(this@TestMLActivity)

                    // Uruchamiamy test i przekazujemy funkcję aktualizującą status
                    benchmark.runBenchmark { progressText ->
                        currentStatus = progressText
                    }
                }

                currentStatus = "Testy zakończone pomyślnie!"
                finalResults = result
            }

            // Twój UI
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Benchmark LSTM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Pokazuje obecny status (np. "Testowanie GPU...")
                    Text(text = currentStatus, color = Color.Gray)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Pokazuje wyniki na samym końcu
                    Text(text = finalResults, color = Color.Blue, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}