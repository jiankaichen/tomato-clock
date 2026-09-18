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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jiankaichen.clock.data.AppStore
import dev.jiankaichen.clock.data.Session
import dev.jiankaichen.clock.data.SessionKind
import dev.jiankaichen.clock.data.TimerPreset
import dev.jiankaichen.clock.timer.CountdownController

/** A timer the user has chosen but not started yet. */
private data class Ready(val label: String, val tag: String, val totalMs: Long, val preset: TimerPreset?)

@Composable
fun TimersScreen(store: AppStore, controller: CountdownController) {
    val sessions by controller.sessions.collectAsStateWithLifecycle()
    val presets by store.presets.collectAsStateWithLifecycle()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimerPreset?>(null) }
    var ready by remember { mutableStateOf<Ready?>(null) }

    val running = sessions.filter { it.kind == SessionKind.TIMER }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorOpen = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New timer") },
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Timers", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 4.dp))
            }
            ready?.let { r ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "ready") {
                    ReadyCard(
                        r,
                        onStart = {
                            if (r.preset != null) controller.startPreset(r.preset) else controller.startCustom(r.totalMs, r.label, r.tag)
                            ready = null
                        },
                        onCancel = { ready = null },
                    )
                }
            }
            items(running, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { s ->
                RunningCard(s, controller)
            }
            if (running.isNotEmpty() || ready != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Presets", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(presets, key = { it.id }) { preset ->
                PresetCard(
                    preset,
                    selected = ready?.preset?.id == preset.id,
                    onSelect = { ready = Ready(preset.name, preset.tag, preset.totalMs, preset) },
                    onEdit = { editing = preset; editorOpen = true },
                )
            }
        }
    }

    if (editorOpen) {
        PresetEditorDialog(
            initial = editing,
            onDismiss = { editorOpen = false },
            onSave = { store.upsertPreset(it); editorOpen = false },
            onDelete = { store.deletePreset(it); editorOpen = false },
            onUseOnce = { name, tag, ms -> ready = Ready(name, tag, ms, null); editorOpen = false },
        )
    }
}

@Composable
private fun ReadyCard(r: Ready, onStart: () -> Unit, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = GitHub.Surface)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatrixTag(r.tag, large = true)
                Spacer(Modifier.width(12.dp))
                Text(r.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("READY", color = GitHub.Muted, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(16.dp))
            PixelDigits(formatMs(r.totalMs), Modifier.fillMaxWidth(0.8f), lit = GitHub.Green1)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onStart, modifier = Modifier.weight(2f).height(52.dp)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun RunningCard(s: Session, controller: CountdownController) {
    val remaining = rememberRemaining(s)
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatrixTag(s.tag)
                Spacer(Modifier.width(10.dp))
                Text(s.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (s.finished) "TIME'S UP" else if (s.paused) "PAUSED" else "RUNNING",
                    color = if (s.finished) GitHub.Red else GitHub.Muted,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PixelDigits(formatMs(remaining), Modifier.fillMaxWidth(0.7f), lit = if (s.running) GitHub.Green3 else GitHub.Green1)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                if (s.finished) {
                    Button(onClick = { controller.dismiss(s.id) }) { Text("Stop") }
                } else {
                    FilledTonalButton(onClick = { controller.addMinute(s.id) }) { Text("+1:00") }
                    FilledIconButton(onClick = { controller.togglePause(s.id) }) {
                        Icon(
                            if (s.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (s.paused) "Resume" else "Pause",
                        )
                    }
                    OutlinedButton(onClick = { controller.cancel(s.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: TimerPreset, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = if (selected) GitHub.Green0 else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MatrixTag(preset.tag) }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${preset.name}") }
            }
            Text(preset.name, style = MaterialTheme.typography.titleMedium)
            Text(
                formatMs(preset.totalMs),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PresetEditorDialog(
    initial: TimerPreset?,
    onDismiss: () -> Unit,
    onSave: (TimerPreset) -> Unit,
    onDelete: (String) -> Unit,
    onUseOnce: (String, String, Long) -> Unit,
) {
    var tag by remember { mutableStateOf(initial?.tag ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    val total = initial?.seconds ?: 0
    var hours by remember { mutableStateOf(if (total / 3600 > 0) (total / 3600).toString() else "") }
    var minutes by remember { mutableStateOf(if (total > 0) ((total % 3600) / 60).toString() else "") }
    var seconds by remember { mutableStateOf(if (total % 60 > 0) (total % 60).toString() else "") }

    val totalSeconds = (hours.toIntOrNull() ?: 0) * 3600 + (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
    val valid = totalSeconds > 0 && name.isNotBlank()
    val effectiveTag = tag.ifBlank { AppStore.tagFromName(name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New timer" else "Edit timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { v -> if (v.length <= 3 && v.all { it.isLetterOrDigit() }) tag = v.uppercase() },
                        label = { Text("Tag") }, placeholder = { Text(AppStore.tagFromName(name)) },
                        singleLine = true, modifier = Modifier.width(96.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    )
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(hours, { hours = it }, "Hours", Modifier.weight(1f))
                    NumberField(minutes, { minutes = it }, "Min", Modifier.weight(1f))
                    NumberField(seconds, { seconds = it }, "Sec", Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(
                        onClick = { onUseOnce(name.ifBlank { "Timer" }, effectiveTag, totalSeconds * 1000L) },
                        enabled = totalSeconds > 0,
                    ) { Text("Use once without saving") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(TimerPreset(initial?.id ?: AppStore.newId(), name.trim(), totalSeconds, effectiveTag)) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 3 && v.all(Char::isDigit)) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
