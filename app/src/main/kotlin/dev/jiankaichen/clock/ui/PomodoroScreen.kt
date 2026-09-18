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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jiankaichen.clock.data.AppStore
import dev.jiankaichen.clock.data.PomodoroSettings
import dev.jiankaichen.clock.data.SessionKind
import dev.jiankaichen.clock.timer.CountdownController

@Composable
fun PomodoroScreen(store: AppStore, controller: CountdownController) {
    val sessions by controller.sessions.collectAsStateWithLifecycle()
    val settings by store.pomodoro.collectAsStateWithLifecycle()
    val s = sessions.firstOrNull { it.kind.isPomodoro }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Pomodoro", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            MatrixTag(if (s != null) phaseTag(s.kind) else "IDLE", large = true)
        }
        Spacer(Modifier.height(16.dp))

        if (s != null) {
            CycleDots(completed = s.pomodoroCycle % settings.cyclesBeforeLongBreak, total = settings.cyclesBeforeLongBreak, working = s.kind == SessionKind.WORK)
            Spacer(Modifier.height(16.dp))
            CountdownDial(s, Modifier.fillMaxWidth(0.85f))
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                OutlinedIconButton(onClick = { controller.cancel(s.id) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Stop")
                }
                FilledIconButton(onClick = { controller.togglePause(s.id) }, modifier = Modifier.size(80.dp)) {
                    Icon(
                        if (s.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (s.paused) "Resume" else "Pause",
                        modifier = Modifier.size(40.dp),
                    )
                }
                FilledTonalIconButton(onClick = { controller.skipPhase(s.id) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Skip phase")
                }
            }
            if (s.paused && s.remainingMs == s.totalMs) {
                Spacer(Modifier.height(12.dp))
                Text("Ready for the next phase. Press play when you are.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(
                "${settings.workMinutes} min focus, ${settings.shortBreakMinutes} min break, " +
                    "${settings.longBreakMinutes} min long break every ${settings.cyclesBeforeLongBreak} rounds.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = controller::startPomodoro, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Start focus", fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
        SettingsCard(settings, onChange = store::savePomodoro)
    }
}

@Composable
private fun CycleDots(completed: Int, total: Int, working: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(total) { i ->
            val filled = i < completed || (working && i == completed)
            val color = when {
                i < completed -> MaterialTheme.colorScheme.primary
                filled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(Modifier.size(14.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun SettingsCard(settings: PomodoroSettings, onChange: (PomodoroSettings) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            Stepper("Focus", settings.workMinutes, 1, 120, "min") { onChange(settings.copy(workMinutes = it)) }
            Stepper("Short break", settings.shortBreakMinutes, 1, 60, "min") { onChange(settings.copy(shortBreakMinutes = it)) }
            Stepper("Long break", settings.longBreakMinutes, 1, 120, "min") { onChange(settings.copy(longBreakMinutes = it)) }
            Stepper("Rounds before long break", settings.cyclesBeforeLongBreak, 1, 12, "") { onChange(settings.copy(cyclesBeforeLongBreak = it)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-start next phase", Modifier.weight(1f))
                Switch(checked = settings.autoStartNext, onCheckedChange = { onChange(settings.copy(autoStartNext = it)) })
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label")
        }
        Text(
            if (unit.isEmpty()) "$value" else "$value $unit",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
        )
        IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase $label")
        }
    }
}
