package com.kemai.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kemai.app.model.pendingVisitCount

/** 闹钟触发：发通知，然后把下一次续订上 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val data = ReminderScheduler.readData(context)
        val settings = data.settings.reminder
        if (settings.enabled) {
            ReminderScheduler.notifyVisit(context, data.pendingVisitCount())
        }
        // 一次性闹钟，触发后必须自己续订下一次
        ReminderScheduler.reschedule(context, settings)
    }
}
