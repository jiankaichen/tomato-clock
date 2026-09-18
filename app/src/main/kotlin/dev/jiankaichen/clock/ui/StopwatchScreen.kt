package dev.jiankaichen.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jiankaichen.clock.data.Stopwatch
import dev.jiankaichen.clock.timer.StopwatchController
import kotlinx.coroutines.delay

/** mm:ss.cc, or h:mm:ss.cc past an hour. */
fun formatStopwatch(ms: Long): String {
    val centis = (ms / 10) % 100
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, centis) else "%02d:%02d.%02d".format(m, s, centis)
}

@Composable
private fun rememberElapsed(sw: Stopwatch): Long {
    var elapsed by remember(sw) { mutableLongStateOf(sw.elapsedNow()) }
    LaunchedEffect(sw) {
        while (sw.running) {
            elapsed = sw.elapsedNow()
            delay(33)
        }
    }
    return elapsed
}

@Composable
fun StopwatchScreen(controller: StopwatchController) {
    val list by controller.stopwatches.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { controller.add() },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New stopwatch") },
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Stopwatch", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 4.dp)) }
            items(list, key = { it.id }) { sw ->
                StopwatchCard(sw, controller, canDelete = list.size > 1)
            }
        }
    }
}

@Composable
private fun StopwatchCard(sw: Stopwatch, controller: StopwatchController, canDelete: Boolean) {
    val elapsed = rememberElapsed(sw)
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatrixTag("SW")
                Spacer(Modifier.width(10.dp))
                Text(sw.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (sw.running) "RUNNING" else if (sw.started) "PAUSED" else "READY",
                    color = GitHub.Muted, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium,
                )
                if (canDelete) {
                    IconButton(onClick = { controller.remove(sw.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete ${sw.name}", tint = GitHub.Muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PixelDigits(formatStopwatch(elapsed), Modifier.fillMaxWidth(0.92f), lit = if (sw.running) GitHub.Green3 else GitHub.Green1)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { if (sw.running) controller.lap(sw.id) else controller.reset(sw.id) },
                    enabled = sw.started,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        if (sw.running) Icons.Rounded.Flag else Icons.Rounded.Refresh,
                        contentDescription = if (sw.running) "Lap" else "Reset",
                    )
                }
                FilledIconButton(onClick = { controller.toggle(sw.id) }, modifier = Modifier.size(68.dp)) {
                    Icon(
                        if (sw.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (sw.running) "Pause" else "Start",
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            if (sw.laps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                sw.laps.asReversed().forEachIndexed { i, total ->
                    val index = sw.laps.size - i
                    val previous = if (index >= 2) sw.laps[index - 2] else 0L
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Lap $index", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatStopwatch(total - previous),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium),
                        )
                        Spacer(Modifier.width(20.dp))
                        Text(
                            formatStopwatch(total),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
