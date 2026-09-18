package dev.jiankaichen.clock.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jiankaichen.clock.timer.CountdownController
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ClockScreen(controller: CountdownController) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val sessions by controller.sessions.collectAsStateWithLifecycle()

    val hm = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm") }
    val ss = remember { DateTimeFormatter.ofPattern("ss") }
    val ampm = remember { DateTimeFormatter.ofPattern("a") }
    val date = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Hours and minutes as the big contribution grid; seconds as a smaller grid beside it.
        PixelDigits(hm.format(now), Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelDigits(ss.format(now), Modifier.width(56.dp), lit = GitHub.Green2)
            if (!is24) {
                Spacer(Modifier.width(12.dp))
                Text(ampm.format(now), style = MaterialTheme.typography.titleMedium, color = GitHub.Muted)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(date.format(now), style = MaterialTheme.typography.titleLarge, color = GitHub.Muted)

        if (sessions.isNotEmpty()) Spacer(Modifier.height(32.dp))
        sessions.forEach { s ->
            val endsAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(s.endAtWallMs), ZoneId.systemDefault())
            val text = when {
                s.finished -> "${s.label} finished"
                s.paused -> "${s.label} · paused at ${formatMs(s.remainingMs)}"
                else -> "${s.label} · ends ${hm.format(endsAt)}"
            }
            Card(Modifier.padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MatrixTag(s.tag)
                    Spacer(Modifier.width(10.dp))
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
