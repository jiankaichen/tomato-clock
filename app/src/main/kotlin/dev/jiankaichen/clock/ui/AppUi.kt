package dev.jiankaichen.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jiankaichen.clock.data.AppStore
import dev.jiankaichen.clock.data.Session
import dev.jiankaichen.clock.timer.CountdownController
import dev.jiankaichen.clock.timer.StopwatchController

private enum class Tab(val label: String, val icon: ImageVector) {
    Timers("Timers", Icons.Rounded.HourglassBottom),
    Pomodoro("Pomodoro", Icons.Rounded.Spa),
    Stopwatch("Stopwatch", Icons.Rounded.Timer),
    Clock("Clock", Icons.Rounded.Schedule),
}

@Composable
fun AppUi(store: AppStore, controller: CountdownController, stopwatch: StopwatchController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sessions by controller.sessions.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (Tab.entries[tab]) {
                    Tab.Timers -> TimersScreen(store, controller)
                    Tab.Pomodoro -> PomodoroScreen(store, controller)
                    Tab.Stopwatch -> StopwatchScreen(stopwatch)
                    Tab.Clock -> ClockScreen(controller)
                }
            }
        }
        sessions.firstOrNull { it.finished }?.let { FinishedOverlay(it, controller) }
    }
}

@Composable
private fun FinishedOverlay(session: Session, controller: CountdownController) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MatrixTag(session.tag, large = true)
                Spacer(Modifier.height(12.dp))
                Text("Time's up", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(session.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                if (session.kind.isPomodoro) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { controller.cancel(session.id) }, modifier = Modifier.weight(1f)) { Text("Stop") }
                        Button(onClick = { controller.dismiss(session.id) }, modifier = Modifier.weight(1f)) { Text("Start next") }
                    }
                } else {
                    Button(onClick = { controller.dismiss(session.id) }, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
                }
            }
        }
    }
}
