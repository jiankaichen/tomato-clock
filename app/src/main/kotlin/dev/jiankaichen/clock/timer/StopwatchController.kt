package dev.jiankaichen.clock.timer

import android.content.Context
import android.content.Intent
import dev.jiankaichen.clock.data.AppStore
import dev.jiankaichen.clock.data.Stopwatch
import dev.jiankaichen.clock.widget.ClockWidgetProvider
import kotlinx.coroutines.flow.StateFlow

/** Any number of stopwatches. Persisted through [AppStore]; the widget shows running ones. */
class StopwatchController(private val context: Context, private val store: AppStore) {

    val stopwatches: StateFlow<List<Stopwatch>> = store.stopwatches

    fun add(): Stopwatch {
        val n = stopwatches.value.size + 1
        val sw = Stopwatch(AppStore.newId(), "Stopwatch $n")
        apply(stopwatches.value + sw)
        return sw
    }

    fun remove(id: String) = apply(stopwatches.value.filterNot { it.id == id })

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.ifBlank { it.name }) }

    fun start(id: String) = update(id) { s ->
        if (s.running) s else {
            val now = System.currentTimeMillis()
            store.resetWidgetPages()
            s.copy(running = true, startedAtWallMs = now, lastStartedWallMs = now)
        }
    }

    fun pause(id: String) = update(id) { s ->
        if (!s.running) s else s.copy(running = false, accumulatedMs = s.elapsedNow(), startedAtWallMs = 0L)
    }

    fun toggle(id: String) {
        val s = stopwatches.value.firstOrNull { it.id == id } ?: return
        if (s.running) pause(id) else start(id)
    }

    fun lap(id: String) = update(id) { s -> if (s.running) s.copy(laps = s.laps + s.elapsedNow()) else s }

    fun reset(id: String) = update(id) { Stopwatch(it.id, it.name) }

    private fun update(id: String, f: (Stopwatch) -> Stopwatch) {
        val list = stopwatches.value
        if (list.none { it.id == id }) return
        apply(list.map { if (it.id == id) f(it) else it })
    }

    private fun apply(list: List<Stopwatch>) {
        store.saveStopwatches(list)
        // The service ticks the widget while a stopwatch runs; it stops itself when nothing needs it.
        val intent = Intent(context, CountdownService::class.java)
        if (list.any { it.running }) context.startForegroundService(intent.setAction(CountdownService.ACTION_SYNC))
        else if (store.sessions.value.isEmpty()) context.stopService(intent)
        ClockWidgetProvider.refreshAll(context)
    }
}
