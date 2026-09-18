package dev.jiankaichen.clock.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jiankaichen.clock.ClockApp

class CountdownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_SESSION_ID)
        val controller = ClockApp.instance.countdown
        if (id != null) controller.onFinished(id) else controller.finishDue()
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}
