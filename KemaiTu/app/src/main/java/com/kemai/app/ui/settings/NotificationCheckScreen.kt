package com.kemai.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kemai.app.R
import com.kemai.app.data.AppRepository
import com.kemai.app.model.pendingVisitCount
import com.kemai.app.reminder.ReminderScheduler
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.components.SectionLabel
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 通知是否正常？
 *
 * 你遇到过 VIVO 的「智能控制后台耗电」偶发拦截通知 —— 这类问题不能让人去猜。
 * 所以这一页把「通知为什么可能不响」逐条摊开，并给出对应机型的设置路径。
 */
@Composable
fun NotificationCheckScreen(
    repo: AppRepository,
    onBack: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    val context = LocalContext.current

    // 每次回到这一页都重新读一遍系统状态
    var refresh by remember { mutableIntStateOf(0) }
    var testAlarmAt by remember { mutableStateOf(0L) }
    val notificationsOn = remember(refresh) { ReminderScheduler.notificationsEnabled(context) }
    val batteryFree = remember(refresh) { ReminderScheduler.ignoringBatteryOptimizations(context) }
    val exactOk = remember(refresh) { ReminderScheduler.canScheduleExact(context) }
    // 先确保渠道存在，否则第一次进这一页会读到 null，误报成"渠道级别偏低"
    val channelImportance = remember(refresh) {
        ReminderScheduler.ensureChannel(context)
        ReminderScheduler.channelImportance(context)
    }
    val nextAt = remember(refresh) { ReminderScheduler.savedNextTrigger(context) }
    val pending = repo.data.pendingVisitCount()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    // 从系统设置页返回时自动重读一遍状态，不用手动退出去再进来
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pal.canvasBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(R.drawable.ic_back, contentDescription = "返回", tint = pal.textPrimary, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "通知是否正常？",
                style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                color = pal.textPrimary,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "系统通知可能被手机的省电策略拦住。下面几项挨个看一眼，"
                    + "就能知道为什么没响、该去哪儿改。",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            SectionLabel("逐项检查")
            CheckRow(
                title = "通知权限",
                ok = notificationsOn,
                okText = "已开启",
                badText = "未开启，通知发不出来",
                actionText = if (notificationsOn) null else "去开启",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationsOn
                    ) {
                        permissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                    } else {
                        openAppNotificationSettings(context)
                    }
                },
            )
            CheckRow(
                title = "系统电池优化",
                ok = batteryFree,
                okText = "已加入白名单，后台不会被冻结",
                badText = "未加入白名单。注意这和厂商的「后台耗电管理」是"
                    + "两套不同的设置，两个都设上才最稳",
                actionText = if (batteryFree) null else "去开启",
                onAction = { requestIgnoreBatteryOptimizations(context) },
            )
            CheckRow(
                title = "精确闹钟",
                ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) exactOk else true,
                okText = "已授权，提醒会准点送达",
                badText = "未授权 —— 提醒仍然会发，但可能晚几分钟到几十分钟。"
                    + "开了它才能在省电模式下准点响",
                actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactOk)
                    "去开启" else null,
                onAction = { openExactAlarmSettings(context) },
            )
            CheckRow(
                title = "厂商后台策略",
                ok = null,
                okText = "",
                badText = "无法自动读取，需要你手动确认一次",
                actionText = "查看步骤",
                onAction = { openAppDetailSettings(context) },
            )
            CheckRow(
                title = "通知能不能弹出来",
                ok = channelImportance >= 4,
                okText = "渠道级别够高，会在屏幕上方弹出",
                badText = "渠道级别偏低，只会静静进通知栏、不弹出来。"
                    + "到系统设置 → 通知 → 客脉图里把这个渠道调成「高」",
                actionText = if (channelImportance in 0..3) "去设置" else null,
                onAction = { openAppNotificationSettings(context) },
            )
            CheckRow(
                title = "下次提醒时间",
                ok = true,
                okText = formatNext(nextAt),
                badText = "提醒已关闭",
                actionText = null,
                onAction = {},
            )

            Spacer(Modifier.height(16.dp))

            // ---------- 测试通知 ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.accent)
                    .clickable {
                        ReminderScheduler.notifyVisit(context, pending, test = true)
                        refresh++
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "发一条测试通知",
                    style = TextStyle(fontSize = d.body, fontWeight = FontWeight.SemiBold),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = "按一下马上就能知道通道通不通，不用等到下周一。",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            // ---------- 测试闹钟（走完整链路） ----------
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.surfaceVariant)
                    .clickable {
                        ReminderScheduler.scheduleTestAlarm(context, 60)
                        testAlarmAt = System.currentTimeMillis() + 60_000L
                        refresh++
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "测试闹钟（1 分钟后触发）",
                    style = TextStyle(fontSize = d.body, fontWeight = FontWeight.SemiBold),
                    color = pal.accent,
                )
            }
            Text(
                text = if (testAlarmAt > 0)
                    "已排上：${formatNext(testAlarmAt)} 会响一次，用来验证「闹钟 → 通知」整条链路。" +
                        "响过之后会自动把正式的每周提醒接回去。"
                else
                    "上面那个按钮只测通知通道；这个按钮测的是完整链路 —— " +
                        "把手机放一边等一会儿，看看通知会不会自己弹出来。",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "说明：拿到「精确闹钟」权限时它会准点响；没拿到就退回系统非精确闹钟，"
                    + "可能晚几分钟到几十分钟 —— 真实提醒也是这个逻辑，属正常现象。",
                style = TextStyle(fontSize = d.tiny),
                color = pal.textSecondary.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp),
            )
            if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Text(
                    text = "⚠️ 当前没有「精确闹钟」权限，所以这次测试也可能不准点，"
                        + "甚至要等很久。想马上验证，先到上面把「精确闹钟」开了再按。",
                    style = TextStyle(fontSize = d.tiny, fontWeight = FontWeight.Medium),
                    color = Color(0xFFB4632A),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ---------- 厂商引导 ----------
            SectionLabel("按你的手机品牌设置")
            BrandGuideCard()

            SectionLabel("无论通知响不响")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.surfaceVariant)
                    .padding(14.dp),
            ) {
                Text(
                    text = "顶部「拜访清单」入口的角标永远是准的 —— "
                        + "只要你打开应用，就一定能看到本周的名单，不会漏跟。"
                        + "通知只是锦上添花。",
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textPrimary,
                )
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    /** null = 无法自动判断 */
    ok: Boolean?,
    okText: String,
    badText: String,
    actionText: String?,
    onAction: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    val mark = when (ok) {
        true -> "✓"
        false -> "!"
        null -> "?"
    }
    val markColor = when (ok) {
        true -> pal.accent
        false -> Color(0xFFB3261E)
        null -> Color(0xFFB58A2B)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(markColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mark,
                style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.Bold),
                color = markColor,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(fontSize = d.body),
                color = pal.textPrimary,
            )
            Text(
                text = if (ok == true) okText else badText,
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
            )
        }
        if (actionText != null) {
            Text(
                text = actionText,
                style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.Medium),
                color = pal.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider.copy(alpha = 0.5f)))
}

@Composable
private fun BrandGuideCard() {
    val pal = palette
    val d = LocalDimens.current
    val brand = remember { ReminderScheduler.brand() }

    val guide: Pair<String, List<String>> = when {
        brand.contains("vivo") || brand.contains("iqoo") ->
            "VIVO / iQOO" to listOf(
                "设置 → 电池 → 后台耗电管理",
                "找到「客脉图」→ 选择「允许后台高耗电」",
                "这一项是 VIVO 上通知偶发不出现最常见的原因",
            )
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
            "小米 / Redmi" to listOf(
                "设置 → 应用设置 → 客脉图 → 省电策略 → 选「无限制」",
                "同一页里把「自启动」也打开",
                "最近任务里给客脉图加锁，避免被一键清理掉",
            )
        brand.contains("huawei") || brand.contains("honor") ->
            "华为 / 荣耀" to listOf(
                "设置 → 应用 → 应用启动管理 → 客脉图",
                "关闭「自动管理」",
                "手动勾选：允许自启动、允许关联启动、允许后台活动",
            )
        brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") ->
            "OPPO / 一加 / realme" to listOf(
                "设置 → 电池 → 应用耗电管理 → 客脉图",
                "允许「后台运行」",
                "同时允许「自动启动」",
            )
        brand.contains("samsung") ->
            "三星" to listOf(
                "设置 → 电池 → 后台使用限制",
                "把「客脉图」从「休眠应用」里移除",
                "顺便关掉它的「自动优化」",
            )
        else ->
            "通用建议" to listOf(
                "设置 → 应用 → 客脉图 → 电池 → 选「不受限制」",
                "允许应用自启动 / 后台活动",
                "在最近任务里给客脉图加锁，别被一键清理",
            )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(pal.surface)
            .padding(14.dp),
    ) {
        Text(
            text = "检测到你的手机品牌：${guide.first}",
            style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
            color = pal.accent,
        )
        Spacer(Modifier.height(8.dp))
        guide.second.forEachIndexed { i, line ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text(
                    text = "${i + 1}.",
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textSecondary,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    text = line,
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------- 跳系统设置 ----------------

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailSettings(context) }
}

private fun openBatterySettings(context: Context) {
    // 优先跳到「电池优化」列表页；不需要额外权限
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailSettings(context) }
}

/**
 * 直接弹出系统的「是否允许应用忽略电池优化」对话框，
 * 比让用户自己去列表里翻要省事得多。需要 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限。
 */
private fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        openBatterySettings(context)
        return
    }
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openBatterySettings(context) }
}

/**
 * 跳到系统「精确闹钟」授权页。
 *
 * Android 12 起这是需要用户手动开的特殊权限，应用没法自己弹窗申请，
 * 只能把用户送到设置页。我们**不强制**：不给也能用，只是提醒可能不准点。
 */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        openAppDetailSettings(context)
        return
    }
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailSettings(context) }
}

private fun openAppDetailSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private val NEXT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun formatNext(millis: Long): String {
    if (millis <= 0L) return "提醒已关闭"
    return runCatching {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(NEXT_FMT)
    }.getOrDefault("—")
}
