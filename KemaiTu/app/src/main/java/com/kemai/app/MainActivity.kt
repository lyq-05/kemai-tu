package com.kemai.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.kemai.app.data.AppRepository
import com.kemai.app.data.DeleteMode
import com.kemai.app.model.descendantsOf
import com.kemai.app.model.pendingVisitCount
import com.kemai.app.reminder.ReminderScheduler
import com.kemai.app.ui.canvas.CanvasController
import com.kemai.app.ui.canvas.CustomerCanvas
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.components.RoundIconButton
import com.kemai.app.ui.editor.CustomerEditorSheet
import com.kemai.app.ui.guide.GuideScreen
import com.kemai.app.ui.products.ProductScreen
import com.kemai.app.ui.search.SearchOverlay
import com.kemai.app.ui.settings.NotificationCheckScreen
import com.kemai.app.ui.settings.SettingsScreen
import com.kemai.app.ui.theme.KemaiTheme
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.fieldLabels
import com.kemai.app.ui.theme.palette
import com.kemai.app.ui.visits.VisitListScreen
import com.kemai.app.util.Dates
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    companion object {
        /** 从通知点进来时带上的标记：直接打开拜访清单 */
        const val EXTRA_OPEN_VISITS = "open_visits"

        /** 调试/演示用：直接打开某个页面（canvas / visits / settings / products / check / guide） */
        const val EXTRA_SCREEN = "screen"
    }

    private var openVisitsFromIntent by mutableStateOf(false)
    private var startScreen by mutableStateOf<String?>(null)

    /**
     * 应用语言要在 Activity 建立资源之前就定下来，否则 Material3 自带的字符串
     * （日期选择器的 "Select date" 之类）还是跟随系统语言。
     * 这里直接从数据文件里读语言设置，先把 Context 换掉再 super。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val tag = readLanguageTag(newBase)
        val locale = java.util.Locale.forLanguageTag(tag)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openVisitsFromIntent = intent?.getBooleanExtra(EXTRA_OPEN_VISITS, false) == true
        startScreen = intent?.getStringExtra(EXTRA_SCREEN)
        val repo = (application as KemaiApplication).repo
        applyAppLocale(this, repo.data.settings.language)
        // 通知渠道尽早建好；顺手把旧版那个「默认重要性、不会横幅弹出」的渠道清掉，
        // 免得用户在系统设置里看到两个同名的「拜访提醒」。
        com.kemai.app.reminder.ReminderScheduler.ensureChannel(this)
        setContent {
            val settings = repo.data.settings
            KemaiTheme(
                themeMode = settings.theme,
                fontScale = settings.fontScale,
                labels = settings.fieldLabels,
                mode = settings.layoutMode,
                colors = settings.colorMode,
                language = settings.language,
            ) {
                KemaiApp(
                    repo = repo,
                    openVisitsOnStart = openVisitsFromIntent,
                    startScreen = startScreen,
                    onVisitsOpened = { openVisitsFromIntent = false },
                )
            }
        }
    }
}

/**
 * 从数据文件里读出语言设置（attachBaseContext 阶段拿不到仓库实例，只能直接读文件）。
 * 任何异常都退回到简体中文。
 */
private fun readLanguageTag(context: android.content.Context): String = runCatching {
    val f = java.io.File(context.filesDir, "kemai_data.json")
    if (!f.exists()) return@runCatching "zh-CN"
    val name = org.json.JSONObject(f.readText())
        .optJSONObject("settings")?.optString("language") ?: "ZH"
    if (name == "EN") "en" else "zh-CN"
}.getOrDefault("zh-CN")

/**
 * 让系统组件（日期选择器、Toast 等）也跟着应用语言走。
 * 只靠 Compose 的 CompositionLocal 管不到这些地方，必须同时改 Locale 默认值和资源配置。
 */
private fun applyAppLocale(context: android.content.Context, language: com.kemai.app.model.AppLanguage) {
    val locale = java.util.Locale.forLanguageTag(language.tag)
    java.util.Locale.setDefault(locale)
    val config = android.content.res.Configuration(context.resources.configuration).apply {
        setLocale(locale)
    }
    @Suppress("DEPRECATION")
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}

private enum class Screen { CANVAS, SETTINGS, PRODUCTS, VISITS, NOTIFICATION_CHECK, GUIDE }

private data class EditorRequest(
    val customerId: String?,
    val referrerId: String? = null,
    val siblingOf: String? = null,
    val nonce: Int,
)

@Composable
private fun KemaiApp(
    repo: AppRepository,
    openVisitsOnStart: Boolean,
    startScreen: String? = null,
    onVisitsOpened: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    val context = LocalContext.current

    var screen by remember {
        mutableStateOf(
            when (startScreen) {
                "visits" -> Screen.VISITS
                "settings" -> Screen.SETTINGS
                "products" -> Screen.PRODUCTS
                "check" -> Screen.NOTIFICATION_CHECK
                "guide" -> Screen.GUIDE
                else -> if (openVisitsOnStart) Screen.VISITS else Screen.CANVAS
            }
        )
    }
    var searchOpen by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    var nonce by remember { mutableIntStateOf(0) }
    val controller = remember { CanvasController() }

    LaunchedEffect(Unit) { if (openVisitsOnStart) onVisitsOpened() }

    // 语言一变就同步给系统组件（日期选择器、Toast 等）
    LaunchedEffect(repo.data.settings.language) {
        applyAppLocale(context, repo.data.settings.language)
    }

    // 提醒设置一变就重新排定闹钟；应用每次打开也会校正一次，防止被系统清理后不再响
    LaunchedEffect(repo.data.settings.reminder) {
        ReminderScheduler.reschedule(context, repo.data.settings.reminder)
    }

    val pendingVisits = repo.data.pendingVisitCount()

    // 返回键：逐层退回，不要一按就退出应用
    BackHandler(enabled = searchOpen || screen != Screen.CANVAS || selectedId != null) {
        when {
            searchOpen -> searchOpen = false
            screen == Screen.GUIDE -> screen = Screen.CANVAS
            screen == Screen.NOTIFICATION_CHECK -> screen = Screen.SETTINGS
            screen == Screen.PRODUCTS -> screen = Screen.SETTINGS
            screen == Screen.VISITS -> screen = Screen.CANVAS
            screen == Screen.SETTINGS -> screen = Screen.CANVAS
            selectedId != null -> selectedId = null
        }
    }

    // 操作结果提示
    LaunchedEffect(repo.toast) {
        repo.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            repo.toast = null
        }
    }

    // ---------------- 备份：系统文件选择器（无需存储权限） ----------------
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(repo.exportJson().toByteArray(Charsets.UTF_8))
            } ?: error("无法写入")
        }.isSuccess
        Toast.makeText(
            context,
            if (ok) "备份已导出（${repo.data.customers.size} 位客户）" else "导出失败",
            Toast.LENGTH_SHORT,
        ).show()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
        } else {
            repo.importJson(text)
            selectedId = null
            controller.fitAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(pal.canvasBg)) {
        when (screen) {
            Screen.CANVAS -> {
                CustomerCanvas(
                    repo = repo,
                    controller = controller,
                    selectedId = selectedId,
                    onSelect = { selectedId = it },
                    onOpenEditor = { editor = EditorRequest(it, nonce = nonce++) },
                    onOverflowMenu = { selectedId = it },
                    onCreateFirst = { editor = EditorRequest(null, nonce = nonce++) },
                    modifier = Modifier.fillMaxSize(),
                )

                // ---------- 顶部栏 + 本周待拜访横幅 ----------
                Column(modifier = Modifier.align(Alignment.TopCenter)) {
                    TopBar(
                        pendingVisits = pendingVisits,
                        onSearch = { searchOpen = true },
                        onSettings = { screen = Screen.SETTINGS },
                        onVisits = { screen = Screen.VISITS },
                        onGuide = { screen = Screen.GUIDE },
                    )
                    VisitBanner(
                        visible = pendingVisits > 0 &&
                            repo.data.settings.reminder.bannerDismissedCycle != Dates.currentWeekStartIso(),
                        pending = pendingVisits,
                        onOpen = { screen = Screen.VISITS },
                        onDismiss = { repo.dismissBannerForThisCycle() },
                    )
                }

                // ---------- 右下角：适应全图 ----------
                // 不再放「＋」：新建客户走卡片上的「加下级 / 加同级」，
                // 「＋」建的是根客户，还得填介绍人，并不是常用路径。
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 18.dp, bottom = if (selectedId != null) 92.dp else 22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(pal.surface)
                        .clickable { controller.fitAll() }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KemaiIcon(
                        id = R.drawable.ic_fit,
                        contentDescription = null,
                        tint = pal.textPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "适应全图",
                        style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.Medium),
                        color = pal.textPrimary,
                    )
                }

                // ---------- 选中节点的操作条 ----------
                val sel = selectedId?.let { id -> repo.data.customers.firstOrNull { it.id == id } }
                if (sel != null) {
                    ActionBar(
                        collapsed = sel.collapsed,
                        onAddChild = { editor = EditorRequest(null, referrerId = sel.id, nonce = nonce++) },
                        onAddSibling = { editor = EditorRequest(null, siblingOf = sel.id, nonce = nonce++) },
                        onEdit = { editor = EditorRequest(sel.id, nonce = nonce++) },
                        onToggleCollapse = { repo.toggleCollapse(sel.id) },
                        onDelete = { deleteTarget = sel.id },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 18.dp),
                    )
                }
            }

            Screen.SETTINGS -> SettingsScreen(
                repo = repo,
                onBack = { screen = Screen.CANVAS },
                onOpenProducts = { screen = Screen.PRODUCTS },
                onExport = {
                    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                    exportLauncher.launch("客脉图备份_$stamp.json")
                },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onClearData = { clearConfirm = true },
                onOpenNotificationCheck = { screen = Screen.NOTIFICATION_CHECK },
                onOpenVisits = { screen = Screen.VISITS },
                notificationStatus = if (ReminderScheduler.notificationsEnabled(context)) "正常" else "待检查",
            )

            Screen.PRODUCTS -> ProductScreen(
                repo = repo,
                onBack = { screen = Screen.SETTINGS },
            )

            Screen.VISITS -> VisitListScreen(
                repo = repo,
                onBack = { screen = Screen.CANVAS },
                onOpenCustomer = { id ->
                    screen = Screen.CANVAS
                    selectedId = id
                    controller.focus(id)
                },
                onOpenReminderSettings = { screen = Screen.SETTINGS },
            )

            Screen.NOTIFICATION_CHECK -> NotificationCheckScreen(
                repo = repo,
                onBack = { screen = Screen.SETTINGS },
            )

            Screen.GUIDE -> GuideScreen(
                repo = repo,
                onBack = { screen = Screen.CANVAS },
            )
        }

        if (searchOpen) {
            SearchOverlay(
                repo = repo,
                onClose = { searchOpen = false },
                onPick = { id ->
                    searchOpen = false
                    selectedId = id
                    controller.focus(id)
                },
                onCreateNew = { name ->
                    searchOpen = false
                    val c = repo.addCustomer(name)
                    selectedId = c.id
                    editor = EditorRequest(c.id, nonce = nonce++)
                },
            )
        }
    }

    // ---------------- 客户编辑面板 ----------------
    editor?.let { req ->
        key(req.nonce) {
            CustomerEditorSheet(
                repo = repo,
                customerId = req.customerId,
                defaultReferrerId = req.referrerId,
                defaultSiblingOf = req.siblingOf,
                onDismiss = { editor = null },
                onSaved = { id ->
                    editor = null
                    selectedId = id
                    controller.focus(id)
                },
                onRequestDelete = { id ->
                    editor = null
                    deleteTarget = id
                },
            )
        }
    }

    // ---------------- 删除确认 ----------------
    deleteTarget?.let { id ->
        val target = repo.data.customers.firstOrNull { it.id == id }
        if (target == null) {
            deleteTarget = null
        } else {
            val childCount = repo.data.customers.count { it.referrerId == id }
            val subtreeCount = repo.data.descendantsOf(id).size
            var mode by remember(id) { mutableStateOf(DeleteMode.PROMOTE_CHILDREN) }

            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                containerColor = pal.surface,
                title = {
                    Text(
                        text = "删除「${target.name}」？",
                        style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                    )
                },
                text = {
                    Column {
                        if (childCount > 0) {
                            Text(
                                text = "该客户名下还有 $childCount 位下级客户。",
                                style = TextStyle(fontSize = d.body),
                                color = pal.textSecondary,
                            )
                            Spacer(Modifier.height(14.dp))
                            ChoiceRow(
                                title = "下级上提（推荐）",
                                desc = "下级变成根客户，数据全部保留",
                                selected = mode == DeleteMode.PROMOTE_CHILDREN,
                                onClick = { mode = DeleteMode.PROMOTE_CHILDREN },
                            )
                            Spacer(Modifier.height(6.dp))
                            ChoiceRow(
                                title = "连同下级一起删除",
                                desc = "将一并删除 ${subtreeCount + 1} 位客户，不可恢复",
                                selected = mode == DeleteMode.DELETE_SUBTREE,
                                onClick = { mode = DeleteMode.DELETE_SUBTREE },
                            )
                        } else {
                            Text(
                                text = "删除后不可恢复。如果之前导出过备份，可以用备份找回。",
                                style = TextStyle(fontSize = d.body),
                                color = pal.textSecondary,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        repo.deleteCustomer(id, mode)
                        if (selectedId == id) selectedId = null
                        deleteTarget = null
                    }) { Text("确认删除", color = Color(0xFFB3261E)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                },
            )
        }
    }

    // ---------------- 清空数据确认 ----------------
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            containerColor = pal.surface,
            title = { Text("清空所有数据？", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    text = "将删除全部 ${repo.data.customers.size} 位客户和 ${repo.data.products.size} 个产品。"
                        + "\n清空前会自动存一份回滚备份，如果误删可以通过「导入恢复」找回上一份备份文件。",
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.clearAll()
                    selectedId = null
                    clearConfirm = false
                    Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                }) { Text("确认清空", color = Color(0xFFB3261E)) }
            },
            dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("取消") } },
        )
    }

    // ---------------- 首次进入：提示去看操作指南 ----------------
    if (!repo.data.settings.guidePromptDone) {
        AlertDialog(
            onDismissRequest = { repo.markGuidePromptDone() },
            containerColor = pal.surface,
            title = {
                Text(
                    text = "先看一眼操作指南？",
                    style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = "指南里是带序号标注的真实界面截图，从「建第一位客户」到「拜访提醒」"
                        + "一步步讲清楚，30 秒就能上手。以后随时可以点顶部的 ? 再看。",
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.markGuidePromptDone()
                    screen = Screen.GUIDE
                }) { Text("查看指南") }
            },
            dismissButton = {
                TextButton(onClick = { repo.markGuidePromptDone() }) { Text("忽略") }
            },
        )
    }
}

@Composable
private fun TopBar(
    pendingVisits: Int,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onVisits: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    val d = LocalDimens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(pal.canvasBg.copy(alpha = 0.94f))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 品牌标记：应用图标 + 名称 + 宣传语，放在首页左上角
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(pal.accent),
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(
                    id = R.drawable.ic_brand,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "客脉图",
                    style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                    color = pal.textPrimary,
                )
                Text(
                    text = "客户成脉，往来成图",
                    style = TextStyle(fontSize = d.tiny),
                    color = pal.textSecondary,
                )
            }
            BarIconButton(R.drawable.ic_search, "搜索", onSearch)
            BarIconButton(R.drawable.ic_help, "操作指南", onGuide)
            BarIconButton(R.drawable.ic_settings, "设置", onSettings)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            VisitPill(pendingVisits, onVisits)
        }
    }
}

/** 顶部「拜访 n」入口：角标永远准确，通知被系统拦住也不会漏跟 */
@Composable
private fun VisitPill(pendingVisits: Int, onVisits: () -> Unit) {
    val pal = palette
    val d = LocalDimens.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (pendingVisits > 0) pal.accent.copy(alpha = 0.14f) else pal.surfaceVariant
            )
            .clickable { onVisits() }
            .padding(
                start = 10.dp,
                end = if (pendingVisits > 0) 8.dp else 11.dp,
                top = 5.dp,
                bottom = 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KemaiIcon(
            id = R.drawable.ic_list,
            contentDescription = "拜访清单",
            tint = if (pendingVisits > 0) pal.accent else pal.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "拜访",
            style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.Medium),
            color = if (pendingVisits > 0) pal.accent else pal.textSecondary,
        )
        if (pendingVisits > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD9534F)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (pendingVisits > 99) "99" else "$pendingVisits",
                    style = TextStyle(fontSize = d.tiny, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

/** 打开应用时的一条可关闭提示，不做打断式弹窗 */
@Composable
private fun VisitBanner(
    visible: Boolean,
    pending: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val pal = palette
    val d = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(pal.accent.copy(alpha = 0.12f))
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "本周有 $pending 位客户待拜访",
            style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.Medium),
            color = pal.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "查看",
            style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.SemiBold),
            color = pal.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpen() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            KemaiIcon(
                id = R.drawable.ic_close,
                contentDescription = "本周不再提示",
                tint = pal.textSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun BarIconButton(
    @androidx.annotation.DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
) {
    val pal = palette
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        KemaiIcon(icon, contentDescription = description, tint = pal.textPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ActionBar(
    collapsed: Boolean,
    onAddChild: () -> Unit,
    onAddSibling: () -> Unit,
    onEdit: () -> Unit,
    onToggleCollapse: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(pal.surface)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionItem("加下级", onAddChild)
        ActionItem("加同级", onAddSibling)
        ActionItem("编辑", onEdit)
        ActionItem(if (collapsed) "展开" else "折叠", onToggleCollapse)
        ActionItem("删除", onDelete, danger = true)
    }
}

@Composable
private fun ActionItem(text: String, onClick: () -> Unit, danger: Boolean = false) {
    val pal = palette
    val d = LocalDimens.current
    Text(
        text = text,
        style = TextStyle(fontSize = d.label, fontWeight = FontWeight.Medium),
        color = if (danger) Color(0xFFB3261E) else pal.textPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) pal.accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) pal.accent else pal.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
                color = pal.textPrimary,
            )
            Text(
                text = desc,
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
            )
        }
    }
}
