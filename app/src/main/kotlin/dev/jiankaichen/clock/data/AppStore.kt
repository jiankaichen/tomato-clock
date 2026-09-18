package dev.jiankaichen.clock.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * SharedPreferences + JSON persistence. Kept synchronous on purpose: the widget provider and
 * broadcast receivers need to read state without a coroutine scope.
 */
class AppStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<List<TimerPreset>> = _presets

    private val _pomodoro = MutableStateFlow(load(KEY_POMODORO, PomodoroSettings.serializer()) ?: PomodoroSettings())
    val pomodoro: StateFlow<PomodoroSettings> = _pomodoro

    private val _sessions = MutableStateFlow(load(KEY_SESSIONS, ListSerializer(Session.serializer())) ?: emptyList())
    val sessions: StateFlow<List<Session>> = _sessions

    private val _stopwatches = MutableStateFlow(
        load(KEY_STOPWATCHES, ListSerializer(Stopwatch.serializer())) ?: listOf(Stopwatch(newId(), "Stopwatch 1"))
    )
    val stopwatches: StateFlow<List<Stopwatch>> = _stopwatches

    fun savePresets(list: List<TimerPreset>) {
        _presets.value = list
        save(KEY_PRESETS, ListSerializer(TimerPreset.serializer()), list)
    }

    fun upsertPreset(preset: TimerPreset) {
        val current = _presets.value
        val idx = current.indexOfFirst { it.id == preset.id }
        savePresets(if (idx >= 0) current.toMutableList().also { it[idx] = preset } else current + preset)
    }

    fun deletePreset(id: String) = savePresets(_presets.value.filterNot { it.id == id })

    fun savePomodoro(settings: PomodoroSettings) {
        _pomodoro.value = settings
        save(KEY_POMODORO, PomodoroSettings.serializer(), settings)
    }

    /** Committed synchronously: the alarm receiver and widget read it right after. */
    fun saveSessions(list: List<Session>) {
        _sessions.value = list
        prefs.edit().putString(KEY_SESSIONS, json.encodeToString(ListSerializer(Session.serializer()), list)).commit()
    }

    fun saveStopwatches(list: List<Stopwatch>) {
        _stopwatches.value = list
        save(KEY_STOPWATCHES, ListSerializer(Stopwatch.serializer()), list)
    }

    // Widget paging: which running item each widget instance is showing.
    fun widgetPage(widgetId: Int): Int = prefs.getInt("$KEY_WIDGET_PAGE$widgetId", 0)
    fun setWidgetPage(widgetId: Int, page: Int) = prefs.edit().putInt("$KEY_WIDGET_PAGE$widgetId", page).apply()

    /** Called whenever something starts, so every widget jumps to the newest item. */
    fun resetWidgetPages() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_WIDGET_PAGE) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun <T> save(key: String, serializer: KSerializer<T>, value: T) =
        prefs.edit().putString(key, json.encodeToString(serializer, value)).apply()

    private fun <T> load(key: String, serializer: KSerializer<T>): T? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    private fun loadPresets(): List<TimerPreset> {
        val list = load(KEY_PRESETS, ListSerializer(TimerPreset.serializer())) ?: return defaultPresets()
        // Earlier builds stored an emoji in this field; turn it into a code tag derived from the name.
        return list.map { p -> if (p.tag.all { it.code < 128 }) p else p.copy(tag = tagFromName(p.name)) }
    }

    private fun defaultPresets() = listOf(
        TimerPreset(newId(), "Tea", 3 * 60, "TEA"),
        TimerPreset(newId(), "Eggs", 7 * 60, "EGG"),
        TimerPreset(newId(), "Nap", 20 * 60, "NAP"),
        TimerPreset(newId(), "Laundry", 45 * 60, "WSH"),
    )

    companion object {
        private const val KEY_PRESETS = "presets"
        private const val KEY_POMODORO = "pomodoro"
        private const val KEY_SESSIONS = "sessions_v2"
        private const val KEY_STOPWATCHES = "stopwatches_v2"
        private const val KEY_WIDGET_PAGE = "widget_page_"

        fun newId(): String = UUID.randomUUID().toString()

        fun tagFromName(name: String): String =
            name.filter { it.isLetterOrDigit() }.take(3).uppercase().ifBlank { "TMR" }
    }
}
