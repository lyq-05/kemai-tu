package com.kemai.app.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 在系统把我们的闹钟清掉之后，把提醒重新排上：
 *  - 手机重启（系统闹钟重启后会全部丢失）
 *  - 应用被更新覆盖
 *  - **用户刚刚授予「精确闹钟」权限** —— 这时要立刻改成精确闹钟
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val handled = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        if (!handled) return

        val settings = ReminderScheduler.readData(context).settings.reminder
        ReminderScheduler.reschedule(context, settings)
    }
}
