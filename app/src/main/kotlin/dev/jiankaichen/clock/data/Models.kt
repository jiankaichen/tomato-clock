package dev.jiankaichen.clock.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [tag] is a short uppercase code (up to 3 chars) shown as a terminal-style badge; no emoji. */
@Serializable
data class TimerPreset(
    val id: String,
    val name: String,
    val seconds: Int,
    @SerialName("emoji") val tag: String = "TMR",
) {
    val totalMs: Long get() = seconds * 1000L
}

@Serializable
data class PomodoroSettings(
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    val autoStartNext: Boolean = true,
)

@Serializable
enum class SessionKind {
    TIMER, WORK, SHORT_BREAK, LONG_BREAK;

    val isPomodoro: Boolean get() = this != TIMER
}

/**
 * One countdown. Any number can run at once (at most one Pomodoro phase). Wall-clock based so it
 * survives process death and reboots; [remainingMs] is only meaningful while [paused].
 */
@Serializable
data class Session(
    val id: String,
    val kind: SessionKind,
    val label: String,
    val tag: String,
    val totalMs: Long,
    val endAtWallMs: Long = 0L,
    val remainingMs: Long = 0L,
    val paused: Boolean = false,
    val finished: Boolean = false,
    /** Completed work phases in the current Pomodoro round. */
    val pomodoroCycle: Int = 0,
    val presetId: String? = null,
    /** Last time this session was started or resumed; the widget shows the newest first. */
    val startedAtWallMs: Long = 0L,
) {
    fun remainingNow(now: Long = System.currentTimeMillis()): Long = when {
        finished -> 0L
        paused -> remainingMs
        else -> (endAtWallMs - now).coerceAtLeast(0L)
    }

    val running: Boolean get() = !paused && !finished
}

/** One stopwatch, wall-clock based like [Session]. [accumulatedMs] excludes the current run. */
@Serializable
data class Stopwatch(
    val id: String,
    val name: String,
    val running: Boolean = false,
    val startedAtWallMs: Long = 0L,
    val accumulatedMs: Long = 0L,
    /** Cumulative elapsed time at each lap, oldest first. */
    val laps: List<Long> = emptyList(),
    /** Last time this stopwatch was started or resumed; the widget shows the newest first. */
    val lastStartedWallMs: Long = 0L,
) {
    fun elapsedNow(now: Long = System.currentTimeMillis()): Long =
        accumulatedMs + if (running) (now - startedAtWallMs).coerceAtLeast(0L) else 0L

    val started: Boolean get() = running || accumulatedMs > 0L
}
