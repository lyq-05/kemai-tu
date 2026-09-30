package com.kemai.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kemai.app.BuildConfig
import com.kemai.app.R
import com.kemai.app.data.AppRepository
import com.kemai.app.model.AppLanguage
import com.kemai.app.model.ColorMode
import com.kemai.app.model.FontScale
import com.kemai.app.model.LayoutMode
import com.kemai.app.model.ReminderFrequency
import com.kemai.app.model.ThemeMode
import com.kemai.app.model.newCustomers
import com.kemai.app.ui.components.ActionRow
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.components.KemaiTextField
import com.kemai.app.ui.components.SectionLabel
import com.kemai.app.ui.components.SelectableChip
import com.kemai.app.ui.components.SettingRow
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.fieldLabels
import com.kemai.app.ui.theme.palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repo: AppRepository,
    onBack: () -> Unit,
    onOpenProducts: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearData: () -> Unit,
    onOpenNotificationCheck: () -> Unit,
    onOpenVisits: () -> Unit,
    notificationStatus: String,
) {
    val pal = palette
    val d = LocalDimens.current
    val labels = fieldLabels
    val context = androidx.compose.ui.platform.LocalContext.current
    var editingField by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pal.canvasBg),
    ) {
        // 顶栏
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
                text = "设置",
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
            // ---------- 外观 ----------
            SectionLabel("外观")
            SettingRow(title = "主题", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    SelectableChip(
                        text = when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        },
                        selected = repo.data.settings.theme == mode,
                        onClick = { repo.setTheme(mode) },
                    )
                }
            }

            SettingRow(title = "字号", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                FontScale.entries.forEach { fs ->
                    SelectableChip(
                        text = fs.label,
                        selected = repo.data.settings.fontScale == fs,
                        onClick = { repo.setFontScale(fs) },
                    )
                }
            }

            // ---------- 脉络方向 ----------
            SettingRow(title = "脉络方向", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LayoutMode.entries.forEach { m ->
                    SelectableChip(
                        text = m.label,
                        selected = repo.data.settings.layoutMode == m,
                        onClick = { repo.setLayoutMode(m) },
                    )
                }
            }
            Text(
                text = repo.data.settings.layoutMode.desc,
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )

            // ---------- 卡片配色 ----------
            SettingRow(title = "卡片配色", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorMode.entries.forEach { m ->
                    SelectableChip(
                        text = m.label,
                        selected = repo.data.settings.colorMode == m,
                        onClick = { repo.setColorMode(m) },
                    )
                }
            }
            Text(
                text = repo.data.settings.colorMode.desc,
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )

            // ---------- 语言 ----------
            SettingRow(title = "系统界面语言", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.entries.forEach { l ->
                    SelectableChip(
                        text = l.label,
                        selected = repo.data.settings.language == l,
                        onClick = {
                            if (repo.data.settings.language != l) {
                                repo.setLanguage(l)
                                // 语言要重建 Activity 才能让系统组件也切过来
                                repo.saveNow()
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                    )
                }
            }
            Text(
                text = "控制日期选择器等系统弹窗的语言，默认简体中文。"
                    + "应用内的界面文字目前只有中文，完整英文版留到将来正式发布时再做。",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider.copy(alpha = 0.6f)))

            // ---------- 字段名称 ----------
            SectionLabel("字段名称")
            Text(
                text = "这三个名字可以改成任何叫法，改完编辑面板、搜索提示都会跟着变 —— "
                    + "换成别的行业也照样用。",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingRow(title = "名称字段", value = labels.name, showDivider = false, onClick = { editingField = 0 })
            SettingRow(title = "时间字段", value = labels.cardDate, showDivider = false, onClick = { editingField = 1 })
            SettingRow(title = "产品字段", value = labels.product, showDivider = false, onClick = { editingField = 2 })
            ActionRow(
                title = "恢复默认名称",
                onClick = {
                    repo.setFieldLabel(0, "客户名称")
                    repo.setFieldLabel(1, "首次办卡时间")
                    repo.setFieldLabel(2, "使用过的产品")
                },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider.copy(alpha = 0.6f)))

            // ---------- 数据 ----------
            SectionLabel("数据")
            SettingRow(title = "产品库管理", onClick = onOpenProducts)
            ActionRow(title = "导出备份", onClick = onExport)
            ActionRow(title = "导入恢复", onClick = onImport)
            ActionRow(
                title = "清空所有数据",
                tint = Color(0xFFB3261E),
                onClick = onClearData,
            )

            // ---------- 提醒 ----------
            val reminder = repo.data.settings.reminder
            SectionLabel("提醒")
            SettingRow(
                title = "拜访提醒",
                showDivider = false,
                trailing = {
                    Switch(
                        checked = reminder.enabled,
                        onCheckedChange = { on -> repo.setReminder { it.copy(enabled = on) } },
                    )
                },
            )
            Text(
                text = "开启后会在设定时间发一条系统通知。顶部「拜访清单」入口的角标始终有效，"
                    + "就算通知被系统拦住也不会漏跟。",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            SettingRow(
                title = "通知是否正常？",
                value = notificationStatus,
                onClick = onOpenNotificationCheck,
            )

            SettingRow(title = "提醒频率", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                ReminderFrequency.entries.forEach { f ->
                    SelectableChip(
                        text = f.label,
                        selected = reminder.frequency == f,
                        onClick = { repo.setReminder { it.copy(frequency = f) } },
                    )
                }
            }

            if (reminder.frequency != ReminderFrequency.DAILY) {
                SettingRow(title = "提醒日", showDivider = false)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 14.dp),
                ) {
                    (1..7).forEach { dow ->
                        SelectableChip(
                            text = com.kemai.app.util.Dates.weekdayLabel(dow),
                            selected = reminder.notifyDayOfWeek == dow,
                            onClick = { repo.setReminder { it.copy(notifyDayOfWeek = dow) } },
                        )
                    }
                }
            }

            SettingRow(title = "提醒时间", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 14.dp),
            ) {
                listOf(8, 9, 12, 18, 20).forEach { h ->
                    SelectableChip(
                        text = "%02d:00".format(h),
                        selected = reminder.notifyHour == h,
                        onClick = { repo.setReminder { it.copy(notifyHour = h) } },
                    )
                }
            }

            SettingRow(title = "新客户判定", showDivider = false)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(3, 6, 12).forEach { m ->
                    SelectableChip(
                        text = "最近 $m 个月",
                        selected = reminder.newCustomerMonths == m,
                        onClick = { repo.setReminder { it.copy(newCustomerMonths = m) } },
                    )
                }
            }
            Text(
                text = "首次办卡时间落在这个范围内，就会自动进入「新客户每周拜访」清单。"
                    + "当前符合条件的有 ${repo.data.newCustomers(reminder.newCustomerMonths).size} 位。",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            ActionRow(title = "打开拜访清单", onClick = onOpenVisits)
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider.copy(alpha = 0.6f)))

            // ---------- 关于 ----------
            SectionLabel("关于")
            SettingRow(title = "应用名称", value = "客脉图", showDivider = false)
            SettingRow(title = "宣传语", value = "客户成脉，往来成图", showDivider = false)
            SettingRow(
                title = "当前客户数",
                value = "${repo.data.customers.size} 位",
                showDivider = false,
            )
            SettingRow(
                title = "产品数",
                value = "${repo.data.products.size} 个",
                showDivider = false,
            )

            Spacer(Modifier.height(28.dp))

            // 版本号：固定在设置最底部
            Text(
                text = "客脉图  v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 22.dp),
            )
        }
    }

    // ---------- 改字段名称 ----------
    editingField?.let { which ->
        val currentValue = when (which) {
            0 -> labels.name
            1 -> labels.cardDate
            else -> labels.product
        }
        var text by remember(which) { mutableStateOf(currentValue) }
        val hint = when (which) {
            0 -> "比如：学员 / 房源 / 会员 / 病患"
            1 -> "比如：入学时间 / 带看时间 / 入会时间"
            else -> "比如：报读课程 / 意向户型 / 服务项目"
        }
        AlertDialog(
            onDismissRequest = { editingField = null },
            containerColor = pal.surface,
            title = {
                Text(
                    text = when (which) {
                        0 -> "改「名称」字段的叫法"
                        1 -> "改「时间」字段的叫法"
                        else -> "改「产品」字段的叫法"
                    },
                    style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Column {
                    Text(
                        text = hint,
                        style = TextStyle(fontSize = d.caption),
                        color = pal.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    KemaiTextField(value = text, onValueChange = { text = it }, placeholder = currentValue)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.setFieldLabel(which, text)
                    editingField = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingField = null }) { Text("取消") } },
        )
    }
}
