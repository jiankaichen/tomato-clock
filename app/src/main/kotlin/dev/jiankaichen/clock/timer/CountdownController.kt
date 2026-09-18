package dev.jiankaichen.clock.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.jiankaichen.clock.MainActivity
import dev.jiankaichen.clock.data.AppStore
import dev.jiankaichen.clock.data.PomodoroSettings
import dev.jiankaichen.clock.data.Session
import dev.jiankaichen.clock.data.SessionKind
import dev.jiankaichen.clock.data.TimerPreset
import dev.jiankaichen.clock.widget.ClockWidgetProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * Single writer of countdown state. Any number of timers may run at once, plus at most one
 * Pomodoro phase. Every transition goes through [apply], which persists the list, reschedules the
 * per-session exact alarms, syncs the foreground service, and refreshes the widget.
 */
class CountdownController(private val context: Context, private val store: AppStore) {

    val sessions: StateFlow<List<Session>> = store.sessions
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun session(id: String): Session? = sessions.value.firstOrNull { it.id == id }
    val pomodoro: Session? get() = sessions.value.firstOrNull { it.kind.isPomodoro }

    fun startPreset(preset: TimerPreset) =
        add(Session(AppStore.newId(), SessionKind.TIMER, preset.name, preset.tag, preset.totalMs, presetId = preset.id))

    fun startCustom(totalMs: Long, label: String = "Timer", tag: String = "TMR") =
        add(Session(AppStore.newId(), SessionKind.TIMER, label, tag, totalMs))

    /** Starts a fresh Pomodoro round, replacing any Pomodoro phase already running. */
    fun startPomodoro() {
        val fresh = pomodoroSession(SessionKind.WORK, store.pomodoro.value, cycle = 0)
        apply(sessions.value.filterNot { it.kind.isPomodoro } + started(fresh))
    }

    fun pause(id: String) = update(id) { s -> if (s.running) s.copy(paused = true, remainingMs = s.remainingNow()) else s }

    fun resume(id: String) = update(id) { s ->
        if (s.paused && !s.finished) {
            val now = System.currentTimeMillis()
            s.copy(paused = false, endAtWallMs = now + s.remainingMs, startedAtWallMs = now).also { store.resetWidgetPages() }
        } else s
    }

    fun togglePause(id: String) {
        val s = session(id) ?: return
        if (s.paused) resume(id) else pause(id)
    }

    fun addMinute(id: String) = update(id) { s ->
        when {
            s.finished -> s
            s.paused -> s.copy(remainingMs = s.remainingMs + 60_000L, totalMs = s.totalMs + 60_000L)
            else -> s.copy(endAtWallMs = s.endAtWallMs + 60_000L, totalMs = s.totalMs + 60_000L)
        }
    }

    /** Removes the session entirely. */
    fun cancel(id: String) = update(id) { null }

    /** Alarm fired for one session. Idempotent. */
    fun onFinished(id: String) = update(id) { s -> if (s.finished) s else s.copy(finished = true, paused = false, remainingMs = 0L) }

    /** Backup for the alarm: finish everything whose end time has passed. */
    fun finishDue() {
        val now = System.currentTimeMillis()
        val list = sessions.value
        if (list.none { it.running && it.endAtWallMs <= now }) return
        apply(list.map { if (it.running && it.endAtWallMs <= now) it.copy(finished = true, remainingMs = 0L) else it })
    }

    /** User acknowledged a finished session. Pomodoro advances; a timer goes away. */
    fun dismiss(id: String) {
        val s = session(id) ?: return
        if (s.kind.isPomodoro) advancePomodoro(s, fromFinish = true) else cancel(id)
    }

    fun dismissAllFinished() = sessions.value.filter { it.finished }.forEach { dismiss(it.id) }

    /** Pomodoro: jump to the next phase without ringing. */
    fun skipPhase(id: String) {
        val s = session(id) ?: return
        if (s.kind.isPomodoro) advancePomodoro(s, fromFinish = false)
    }

    /** Re-arm alarms and service after boot or process restart. */
    fun restore() {
        if (sessions.value.isEmpty()) return
        finishDue()
        apply(sessions.value)
    }

    private fun add(session: Session) = apply(sessions.value + started(session))

    private fun started(s: Session): Session {
        val now = System.currentTimeMillis()
        store.resetWidgetPages()
        return s.copy(paused = false, finished = false, endAtWallMs = now + s.totalMs, startedAtWallMs = now)
    }

    private fun advancePomodoro(s: Session, fromFinish: Boolean) {
        val settings = store.pomodoro.value
        val nextCycle = if (s.kind == SessionKind.WORK) s.pomodoroCycle + 1 else s.pomodoroCycle
        val nextKind = when (s.kind) {
            SessionKind.WORK -> if (nextCycle % settings.cyclesBeforeLongBreak == 0) SessionKind.LONG_BREAK else SessionKind.SHORT_BREAK
            else -> SessionKind.WORK
        }
        val next = pomodoroSession(nextKind, settings, nextCycle).copy(id = s.id)
        val replacement =
            if (settings.autoStartNext || !fromFinish) started(next)
            else next.copy(paused = true, remainingMs = next.totalMs, finished = false)
        apply(sessions.value.map { if (it.id == s.id) replacement else it })
    }

    private fun pomodoroSession(kind: SessionKind, s: PomodoroSettings, cycle: Int): Session {
        val minutes = when (kind) {
            SessionKind.WORK -> s.workMinutes
            SessionKind.SHORT_BREAK -> s.shortBreakMinutes
            SessionKind.LONG_BREAK -> s.longBreakMinutes
            SessionKind.TIMER -> error("not a pomodoro kind")
        }
        val (label, tag) = when (kind) {
            SessionKind.WORK -> "Focus" to "FOCUS"
            SessionKind.SHORT_BREAK -> "Short break" to "BREAK"
            SessionKind.LONG_BREAK -> "Long break" to "LONG"
            SessionKind.TIMER -> "" to ""
        }
        return Session(AppStore.newId(), kind, label, tag, minutes * 60_000L, pomodoroCycle = cycle)
    }

    private fun update(id: String, f: (Session) -> Session?) {
        val list = sessions.value
        if (list.none { it.id == id }) return
        apply(list.mapNotNull { if (it.id == id) f(it) else it })
    }

    private fun apply(list: List<Session>) {
        val previous = sessions.value
        store.saveSessions(list)
        syncAlarms(previous, list)
        syncService(list)
        ClockWidgetProvider.refreshAll(context)
    }

    private fun syncAlarms(previous: List<Session>, current: List<Session>) {
        previous.filter { p -> current.none { it.id == p.id } }.forEach { alarmManager.cancel(alarmPendingIntent(it.id)) }
        current.forEach { s ->
            val pi = alarmPendingIntent(s.id)
            if (s.running) {
                val show = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(s.endAtWallMs, show), pi)
            } else {
                alarmManager.cancel(pi)
            }
        }
    }

    private fun syncService(list: List<Session>) {
        val intent = Intent(context, CountdownService::class.java)
        if (list.isEmpty()) context.stopService(intent)
        else context.startForegroundService(intent.setAction(CountdownService.ACTION_SYNC))
    }

    private fun alarmPendingIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context, id.hashCode(),
        Intent(context, CountdownAlarmReceiver::class.java).putExtra(CountdownAlarmReceiver.EXTRA_SESSION_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
