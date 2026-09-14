package com.example.wavehome.ui.wysypisko
//package com.example.wavehome
//
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.Button
//import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.dp
//import androidx.lifecycle.lifecycleScope
//import androidx.room.Room
//import kotlinx.coroutines.launch
//import java.text.SimpleDateFormat
//import java.util.*
//
//class TestBackgroundTasksActivity : ComponentActivity() {
//
//    private lateinit var db: AppDatabase
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // Inicjalizacja bazy danych
//        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "time_log.db")
//            .build()
//
//        // Ustawienie treści
//        setContent {
//            ShowDataFromDatabase()
//        }
//    }
//
//    @Composable
//    fun ShowDataFromDatabase() {
//        // Stan przechowujący wpisy z bazy danych
//        var timeLogs by remember { mutableStateOf<List<TimeLog>>(emptyList()) }
//
//        // Ładowanie danych przy pierwszym kompozycjonowaniu
//        LaunchedEffect(Unit) {
//            lifecycleScope.launch {
//                timeLogs = db.timeLogDao().getAll()
//            }
//        }
//
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text("Witaj w test background tasks!", color = Color.Gray)
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            LazyColumn(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .weight(1f) // Lista zajmuje pozostałą przestrzeń
//            ) {
//                items(timeLogs) { log ->
//                    val formattedTime = formatTimestamp(log.timestamp)
//                    Text("Pobrany czas: $formattedTime", color = Color.Gray)
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Button(
//                onClick = {
//                    lifecycleScope.launch {
//                        val newTimeLog = TimeLog(timestamp = System.currentTimeMillis())
//                        db.timeLogDao().insert(newTimeLog)
//                        timeLogs = db.timeLogDao().getAll()
//                    }
//                },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(8.dp),
//                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
//            ) {
//                Text("Add")
//            }
//
//            Button(
//                onClick = {
//                    lifecycleScope.launch {
//                        db.timeLogDao().deleteAll()
//                        timeLogs = db.timeLogDao().getAll()
//                    }
//                },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(8.dp),
//                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
//            ) {
//                Text("Delete All")
//            }
//        }
//    }
//
//    // Funkcja formatująca timestamp na HH:mm:ss
//    private fun formatTimestamp(timestamp: Long): String {
//        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
//        return sdf.format(Date(timestamp))
//    }
//}