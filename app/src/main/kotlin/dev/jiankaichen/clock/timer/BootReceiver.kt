package dev.jiankaichen.clock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jiankaichen.clock.ClockApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClockApp.instance.countdown.restore()
    }
}
