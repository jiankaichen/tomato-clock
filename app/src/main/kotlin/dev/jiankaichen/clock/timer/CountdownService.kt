package dev.jiankaichen.clock.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import androidx.core.app.ServiceCompat
import dev.jiankaichen.clock.ClockApp
import dev.jiankaichen.clock.MainActivity
import dev.jiankaichen.clock.R
import dev.jiankaichen.clock.data.Session
import dev.jiankaichen.clock.data.Stopwatch
import dev.jiankaichen.clock.ui.formatMs
import dev.jiankaichen.clock.widget.ClockWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that mirrors every countdown as a notification (one per session, grouped
 * under a summary that is the foreground notification), ticks to the earliest end as a backup for
 * the exact alarms, and rings while any session is finished. It also runs while any stopwatch is
 * running, and while anything is live it re-renders the home screen widget once a second (the
 * widget shows a drawn image, so it cannot update itself). Commands from notification actions
 * are forwarded to [CountdownController].
 */
class CountdownService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller get() = ClockApp.instance.countdown
    private val store get() = ClockApp.instance.store
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var tickJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var widgetTickJob: Job? = null
    private val posted = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            combine(controller.sessions, store.stopwatches) { s, w -> s to w }.collect { (s, w) -> render(s, w) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote(summaryNotification(controller.sessions.value, store.stopwatches.value))
        val id = intent?.getStringExtra(EXTRA_SESSION_ID)
        when (intent?.action) {
            ACTION_PAUSE -> id?.let(controller::pause)
            ACTION_RESUME -> id?.let(controller::resume)
            ACTION_ADD_MINUTE -> id?.let(controller::addMinute)
            ACTION_CANCEL -> id?.let(controller::cancel)
            ACTION_DISMISS -> id?.let(controller::dismiss)
            ACTION_SKIP -> id?.let(controller::skipPhase)
        }
        if (!needed(controller.sessions.value, store.stopwatches.value)) stopSelfCleanly()
        return START_STICKY
    }

    override fun onDestroy() {
        Ringer.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun needed(sessions: List<Session>, stopwatches: List<Stopwatch>) =
        sessions.isNotEmpty() || stopwatches.any { it.running }

    private fun render(list: List<Session>, stopwatches: List<Stopwatch>) {
        if (!needed(list, stopwatches)) {
            stopSelfCleanly()
            return
        }
        promote(summaryNotification(list, stopwatches))
        syncWidgetTicker()
        val wanted = list.associateBy { notificationId(it.id) }
        posted.filter { it !in wanted }.forEach { notificationManager.cancel(it) }
        posted.retainAll(wanted.keys)
        wanted.forEach { (nid, s) ->
            notificationManager.notify(nid, sessionNotification(s))
            posted += nid
        }

        tickJob?.cancel()
        val nextEnd = list.filter { it.running }.minOfOrNull { it.endAtWallMs }
        if (nextEnd != null) {
            tickJob = scope.launch {
                delay((nextEnd - System.currentTimeMillis() + 50).coerceAtLeast(0L))
                controller.finishDue()
            }
        }

        ringTimeoutJob?.cancel()
        if (list.any { it.finished }) {
            Ringer.start(this)
            ringTimeoutJob = scope.launch {
                delay(RING_TIMEOUT_MS)
                controller.dismissAllFinished()
            }
        } else {
            Ringer.stop()
        }
    }

    /** While anything is counting, redraw the widget every second, aligned to the wall clock. */
    private fun syncWidgetTicker() {
        val live = ClockWidgetProvider.hasLiveItems()
        if (live && widgetTickJob?.isActive != true) {
            widgetTickJob = scope.launch {
                while (true) {
                    delay(1000L - System.currentTimeMillis() % 1000L)
                    ClockWidgetProvider.refreshAll(this@CountdownService)
                }
            }
        } else if (!live) {
            widgetTickJob?.cancel()
            widgetTickJob = null
        }
    }

    private fun stopSelfCleanly() {
        tickJob?.cancel()
        ringTimeoutJob?.cancel()
        widgetTickJob?.cancel()
        ClockWidgetProvider.refreshAll(this)
        Ringer.stop()
        posted.forEach { notificationManager.cancel(it) }
        posted.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promote(notification: Notification) {
        ServiceCompat.startForeground(this, SUMMARY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun summaryNotification(list: List<Session>, stopwatches: List<Stopwatch>): Notification {
        val finished = list.count { it.finished }
        val running = stopwatches.count { it.running }
        val text = when {
            finished > 0 -> "$finished finished"
            list.isEmpty() -> if (running == 1) "Stopwatch running" else "$running stopwatches running"
            list.size == 1 -> list[0].label
            else -> "${list.size} timers running"
        }
        return Notification.Builder(this, if (finished > 0) ClockApp.CHANNEL_ALARM else ClockApp.CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()
    }

    private fun sessionNotification(s: Session): Notification {
        val title = if (s.kind.isPomodoro) "${s.label} · round ${s.pomodoroCycle + 1}" else s.label
        return if (s.finished) {
            Notification.Builder(this, ClockApp.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Time's up")
                .setContentText(title)
                .setCategory(Notification.CATEGORY_ALARM)
                .setGroup(GROUP)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openApp())
                .setFullScreenIntent(openApp(), true)
                .apply {
                    if (s.kind.isPomodoro) {
                        addAction(action("Start next", ACTION_DISMISS, s.id))
                        addAction(action("Stop", ACTION_CANCEL, s.id))
                    } else {
                        addAction(action("Stop", ACTION_DISMISS, s.id))
                    }
                }
                .build()
        } else {
            Notification.Builder(this, ClockApp.CHANNEL_COUNTDOWN)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setGroup(GROUP)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp())
                .apply {
                    if (s.running) {
                        setShowWhen(true)
                        setWhen(s.endAtWallMs)
                        setUsesChronometer(true)
                        setChronometerCountDown(true)
                        addAction(action("Pause", ACTION_PAUSE, s.id))
                    } else {
                        setContentText("Paused · ${formatMs(s.remainingMs)}")
                        addAction(action("Resume", ACTION_RESUME, s.id))
                    }
                    addAction(action("+1:00", ACTION_ADD_MINUTE, s.id))
                    if (s.kind.isPomodoro) addAction(action("Skip", ACTION_SKIP, s.id))
                    else addAction(action("Stop", ACTION_CANCEL, s.id))
                }
                .build()
        }
    }

    private fun action(title: String, action: String, sessionId: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notification),
            title,
            servicePendingIntent(this, action, sessionId)
        ).build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val SUMMARY_ID = 1
        private const val GROUP = "countdowns"
        private const val RING_TIMEOUT_MS = 2 * 60_000L

        const val ACTION_SYNC = "dev.jiankaichen.clock.SYNC"
        const val ACTION_PAUSE = "dev.jiankaichen.clock.PAUSE"
        const val ACTION_RESUME = "dev.jiankaichen.clock.RESUME"
        const val ACTION_ADD_MINUTE = "dev.jiankaichen.clock.ADD_MINUTE"
        const val ACTION_CANCEL = "dev.jiankaichen.clock.CANCEL"
        const val ACTION_DISMISS = "dev.jiankaichen.clock.DISMISS"
        const val ACTION_SKIP = "dev.jiankaichen.clock.SKIP"
        const val EXTRA_SESSION_ID = "session_id"

        private val ACTIONS = listOf(ACTION_PAUSE, ACTION_RESUME, ACTION_ADD_MINUTE, ACTION_CANCEL, ACTION_DISMISS, ACTION_SKIP)

        fun notificationId(sessionId: String): Int = 1000 + (sessionId.hashCode() and 0x7FFFFF)

        fun servicePendingIntent(context: Context, action: String, sessionId: String): PendingIntent {
            val intent = Intent(context, CountdownService::class.java).setAction(action).putExtra(EXTRA_SESSION_ID, sessionId)
            val code = (sessionId.hashCode() and 0xFFFFF) * 8 + ACTIONS.indexOf(action)
            return PendingIntent.getForegroundService(
                context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
