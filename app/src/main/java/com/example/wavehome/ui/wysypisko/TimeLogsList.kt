package com.example.wavehome.ui.wysypisko

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.wavehome.TimeLog

@Composable
fun TimeLogsList(timeLogs: List<TimeLog>) {
    LazyColumn {
        items(timeLogs) { log ->
            val formattedTime = formatTimestamp(log.timestamp)
            Text(
                text = "Timestamp: $formattedTime",
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    return timestamp.toString()
}
