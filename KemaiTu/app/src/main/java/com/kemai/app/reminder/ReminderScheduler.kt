package com.kemai.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kemai.app.MainActivity
import com.kemai.app.R
import com.kemai.app.data.JsonCodec
import com.kemai.app.model.AppData
import com.kemai.app.model.ReminderFrequency
import com.kemai.app.model.ReminderSettings
import com.kemai.app.model.pendingVisitCount
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * 拜访提醒的调度与通知。
 *
 * 设计取舍（需求文档 7.3，真机反馈后调整）：
 *  - 只用系统自带的 AlarmManager，不引入任何第三方库；
 *  - **会申请「精确闹钟」权限，但不强制**：拿到了就用精确闹钟（准点送达、能穿透省电模式），
 *    没拿到就退回非精确闹钟（可能晚几分钟到几十分钟），功能不中断；
 *  - 每次触发后自动续订下一次；开机、打开应用时也会各校正一次。
 */
object ReminderScheduler {

    /**
     * 通知渠道 ID **带版本号**。
     *
     * 原因：通知渠道的「重要性」一旦创建就不能再改。老版本用的是
     * IMPORTANCE_DEFAULT，这种级别的通知**只进通知栏、不会在屏幕上方横幅弹出**。
     * 想让提醒能弹出来，只能新建一个 IMPORTANCE_HIGH 的渠道。
     */
    const val CHANNEL_ID = "kemai_visit_reminder_v2"
    private const val LEGACY_CHANNEL_ID = "kemai_visit_reminder"

    const val ACTION_REMIND = "com.kemai.app.action.REMIND"
    private const val ALARM_REQUEST = 1001
    private const val NOTIFY_ID = 2001
    private const val PREFS = "kemai_reminder"
    private const val KEY_NEXT = "next_trigger_at"

    // ---------------- 存储读取（接收器里也要用） ----------------

    fun readData(context: Context): AppData {
        val f = File(context.filesDir, "kemai_data.json")
        if (!f.exists()) return AppData()
        return runCatching { JsonCodec.decode(f.readText()) }.getOrDefault(AppData())
    }

    // ---------------- 下次触发时间 ----------------

    fun nextTriggerAt(settings: ReminderSettings, from: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(from).atZone(zone)
        val stepDays = when (settings.frequency) {
            ReminderFrequency.DAILY -> 1L
            ReminderFrequency.WEEKLY -> 7L
            ReminderFrequency.BIWEEKLY -> 14L
        }
        val hour = settings.notifyHour.coerceIn(0, 23)

        var date = now.toLocalDate()
        if (settings.frequency != ReminderFrequency.DAILY) {
            var guard = 0
            while (date.dayOfWeek.value != settings.notifyDayOfWeek && guard < 8) {
                date = date.plusDays(1)
                guard++
            }
        }
        var candidate = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        var guard = 0
        while (candidate <= from && guard < 60) {
            date = date.plusDays(stepDays)
            candidate = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
            guard++
        }
        return candidate
    }

    fun savedNextTrigger(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_NEXT, 0L)

    // ---------------- 调度 ----------------

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 是否已获得「精确闹钟」权限。
     * Android 12（API 31）起这是需要用户手动去系统设置里开的特殊权限；
     * 12 以下没有这个概念，一律视为可用。
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** 排闹钟：有精确权限就用精确的，没有就退回非精确的 —— 功能不中断，只是可能不准点 */
    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exact = canScheduleExactSafe(am)
        runCatching {
            if (exact) {
                // 能穿透 Doze（省电模式），这是提醒能不能按时到的关键
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    private fun canScheduleExactSafe(am: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** 重新排定提醒。关闭提醒时只取消，不清除已有设置 */
    fun reschedule(context: Context, settings: ReminderSettings) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!settings.enabled) {
            prefs.edit().putLong(KEY_NEXT, 0L).apply()
            return
        }
        val at = nextTriggerAt(settings)
        setAlarm(am, at, pi)
        prefs.edit().putLong(KEY_NEXT, at).apply()
    }

    /**
     * 测试用：把闹钟临时改成「N 秒后触发」。
     *
     * 走的是和正式提醒**完全相同**的链路（同一个 PendingIntent、同一个 Receiver、
     * 同一个通知渠道），所以能真实验证「闹钟 → 广播 → 通知」这一段通不通，
     * 不用等到下周一。触发之后 Receiver 会自动把正式排期接回去。
     */
    fun scheduleTestAlarm(context: Context, seconds: Int = 60): Long {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return 0L
        val pi = alarmIntent(context)
        am.cancel(pi)
        val at = System.currentTimeMillis() + seconds * 1000L
        setAlarm(am, at, pi)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_NEXT, at).apply()
        return at
    }

    // ---------------- 通知 ----------------

    /**
     * 建通知渠道。
     *
     * 重要性用 **HIGH**：只有 HIGH 才会在屏幕上方横幅弹出（heads-up）+ 响铃。
     * DEFAULT 只会静静地进通知栏 —— 这正是真机上"通知栏能看到、但没弹出来"的原因。
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        // 删掉老渠道，避免用户在系统设置里看到两个同名渠道
        runCatching { mgr.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "拜访提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "新客户拜访提醒。保持「高」才能在屏幕上方弹出。"
            enableVibration(true)
            setShowBadge(true)
        }
        runCatching { mgr.createNotificationChannel(ch) }
    }

    /** 渠道被用户调低了吗（比如手动改成了「静默」）—— 自检页用来给出提示 */
    fun channelImportance(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return NotificationManager.IMPORTANCE_HIGH
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return -1
        return mgr.getNotificationChannel(CHANNEL_ID)?.importance ?: -1
    }

    /** 发一条拜访提醒。没有通知权限时静默跳过 —— 应用内的角标永远是准的 */
    fun notifyVisit(context: Context, pending: Int, test: Boolean = false) {
        ensureChannel(context)
        val mgr = NotificationManagerCompat.from(context)
        if (!mgr.areNotificationsEnabled()) return

        val text = when {
            test && pending > 0 -> "这是一条测试通知。当前有 $pending 位客户待拜访。"
            test -> "这是一条测试通知 —— 通道正常，到时候你就能收到拜访提醒了。"
            pending > 0 -> "当前有 $pending 位客户待拜访，点开看看吧。"
            else -> "本周的拜访都完成了 👏"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_VISITS, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("客脉图 · 拜访提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            // HIGH + 渠道 HIGH 才会横幅弹出
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching { mgr.notify(NOTIFY_ID, n) }
    }

    // ---------------- 自检页要用的状态 ----------------

    /** 通知总开关是否打开（也可能是系统层面被关掉了） */
    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * 是否已把本应用排除在**系统标准**的电池优化之外。
     *
     * 注意：这和厂商自己的「后台耗电管理 / 允许后台高耗电」是**两套东西**。
     * VIVO、小米等厂商的开关管的是它们自己的后台策略；这里读的是
     * Android 标准的 Doze 白名单。两个都设上才最稳。
     */
    fun ignoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /** 当前手机品牌，用来匹配厂商后台设置引导 */
    fun brand(): String = (Build.BRAND ?: "").lowercase()
}
