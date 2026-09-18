package dev.jiankaichen.clock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import dev.jiankaichen.clock.data.AppStore
import dev.jiankaichen.clock.timer.CountdownController
import dev.jiankaichen.clock.timer.StopwatchController

class ClockApp : Application() {

    lateinit var store: AppStore
        private set
    lateinit var countdown: CountdownController
        private set
    lateinit var stopwatch: StopwatchController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = AppStore(this)
        countdown = CountdownController(this, store)
        stopwatch = StopwatchController(this, store)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNTDOWN, getString(R.string.notif_channel_countdown), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, getString(R.string.notif_channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                // Sound and vibration are driven by Ringer so we control looping and stop.
                setSound(null, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(false)
                setBypassDnd(true)
            }
        )
    }

    companion object {
        const val CHANNEL_COUNTDOWN = "countdown"
        const val CHANNEL_ALARM = "alarm"

        lateinit var instance: ClockApp
            private set
    }
}
