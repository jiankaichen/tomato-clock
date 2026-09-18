package dev.jiankaichen.clock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import dev.jiankaichen.clock.ClockApp
import dev.jiankaichen.clock.MainActivity
import dev.jiankaichen.clock.R
import dev.jiankaichen.clock.ui.formatMs
import dev.jiankaichen.clock.ui.renderPixelBitmap
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 3x1 Matrix-style widget. Digits are drawn as the app's cell grid ([renderPixelBitmap]) into an
 * ImageView, because launchers do not reliably apply custom fonts to widget text.
 *
 * Idle: the current time, as large as the box allows, refreshed by a once-a-minute alarm that is
 * only armed while a widget exists and nothing is running. Active: the most recently started timer
 * or stopwatch, refreshed once a second by [dev.jiankaichen.clock.timer.CountdownService], with
 * `<` and `>` tap zones to page through the others (widgets cannot take horizontal swipes).
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAll(context)

    override fun onEnabled(context: Context) = refreshAll(context)

    override fun onDisabled(context: Context) = cancelMinuteTick(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        manager.updateAppWidget(widgetId, build(context, manager, widgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, ACTION_TICK -> refreshAll(context)
            ACTION_PAGE -> {
                val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val delta = intent.getIntExtra(EXTRA_DELTA, 0)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val store = ClockApp.instance.store
                    store.setWidgetPage(widgetId, store.widgetPage(widgetId) + delta)
                    val manager = AppWidgetManager.getInstance(context)
                    manager.updateAppWidget(widgetId, build(context, manager, widgetId))
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    /** One pageable item: a running, paused or finished timer, or a started stopwatch. */
    private data class Item(val label: String, val text: String, val startedAt: Long)

    companion object {
        const val ACTION_REFRESH = "dev.jiankaichen.clock.WIDGET_REFRESH"
        const val ACTION_PAGE = "dev.jiankaichen.clock.WIDGET_PAGE"
        private const val ACTION_TICK = "dev.jiankaichen.clock.WIDGET_TICK"
        private const val EXTRA_WIDGET_ID = "widget_id"
        private const val EXTRA_DELTA = "delta"
        private const val LIT = 0xFF39FF14.toInt()
        private const val DIM = 0xFF0C2A13.toInt()

        /** Re-renders every widget and arms or disarms the idle minute tick. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
            if (ids.isEmpty()) {
                cancelMinuteTick(context)
                return
            }
            ids.forEach { manager.updateAppWidget(it, build(context, manager, it)) }
            if (items().isEmpty()) scheduleMinuteTick(context) else cancelMinuteTick(context)
        }

        /** True while something is counting, i.e. the service should tick the widget each second. */
        fun hasLiveItems(): Boolean {
            val store = ClockApp.instance.store
            return store.sessions.value.any { it.running } || store.stopwatches.value.any { it.running }
        }

        private fun items(): List<Item> {
            val store = ClockApp.instance.store
            val now = System.currentTimeMillis()
            val timers = store.sessions.value.map { s ->
                val label = when {
                    s.finished -> "${s.tag} · TIME'S UP"
                    s.paused -> "${s.tag} · PAUSED"
                    s.kind.isPomodoro -> "${s.tag} · ${s.pomodoroCycle + 1}"
                    else -> s.tag
                }
                Item(label, formatMs(s.remainingNow(now)), s.startedAtWallMs)
            }
            val stopwatches = store.stopwatches.value.filter { it.started }.map { sw ->
                val name = sw.name.uppercase()
                Item(if (sw.running) "SW · $name" else "SW · $name · PAUSED", formatMs(sw.elapsedNow(now)), sw.lastStartedWallMs)
            }
            return (timers + stopwatches).sortedByDescending { it.startedAt }
        }

        private fun build(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_clock)
            rv.setOnClickPendingIntent(R.id.widget_root, openApp(context))

            // Size the bitmap to the widget's own box so the grid is as large as it can be.
            val options = manager.getAppWidgetOptions(widgetId)
            val density = context.resources.displayMetrics.density
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 220
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 64
            val widthPx = ((widthDp - 24) * density).toInt().coerceIn(120, 1200)
            val heightPx = ((heightDp - 16) * density).toInt().coerceIn(40, 400)

            val list = items()
            if (list.isEmpty()) {
                val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
                val time = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern))
                rv.setViewVisibility(R.id.widget_time_img, View.VISIBLE)
                rv.setViewVisibility(R.id.widget_active, View.GONE)
                rv.setImageViewBitmap(R.id.widget_time_img, renderPixelBitmap(time, widthPx, heightPx, LIT, DIM))
                return rv
            }

            val store = ClockApp.instance.store
            val page = store.widgetPage(widgetId).mod(list.size)
            val item = list[page]

            rv.setViewVisibility(R.id.widget_time_img, View.GONE)
            rv.setViewVisibility(R.id.widget_active, View.VISIBLE)
            rv.setTextViewText(R.id.widget_status_label, if (list.size > 1) "${item.label}  ${page + 1}/${list.size}" else item.label)
            rv.setOnClickPendingIntent(R.id.widget_active_body, openApp(context))
            rv.setImageViewBitmap(
                R.id.widget_digits_img,
                renderPixelBitmap(item.text, (widthPx - 56 * density).toInt().coerceAtLeast(80), (heightPx - 18 * density).toInt().coerceAtLeast(30), LIT, DIM),
            )

            val paging = if (list.size > 1) View.VISIBLE else View.INVISIBLE
            rv.setViewVisibility(R.id.widget_prev, paging)
            rv.setViewVisibility(R.id.widget_next, paging)
            rv.setOnClickPendingIntent(R.id.widget_prev, pageIntent(context, widgetId, -1))
            rv.setOnClickPendingIntent(R.id.widget_next, pageIntent(context, widgetId, +1))
            return rv
        }

        private fun scheduleMinuteTick(context: Context) {
            val now = System.currentTimeMillis()
            val nextMinute = (now / 60_000L + 1) * 60_000L
            // RTC (not WAKEUP): the widget only needs to be right while the screen is on.
            context.getSystemService(AlarmManager::class.java)
                .setExact(AlarmManager.RTC, nextMinute, tickIntent(context))
        }

        private fun cancelMinuteTick(context: Context) =
            context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 7, Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun pageIntent(context: Context, widgetId: Int, delta: Int): PendingIntent = PendingIntent.getBroadcast(
            context, widgetId * 2 + (if (delta > 0) 1 else 0),
            Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_PAGE)
                .putExtra(EXTRA_WIDGET_ID, widgetId).putExtra(EXTRA_DELTA, delta),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
