package com.kemai.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.kemai.app.R
import com.kemai.app.data.AppRepository
import com.kemai.app.model.CustomEntry
import com.kemai.app.model.Customer
import com.kemai.app.model.newId
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.components.KemaiTextField
import com.kemai.app.ui.components.SectionLabel
import com.kemai.app.ui.components.SelectableChip
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.fieldLabels
import com.kemai.app.ui.theme.palette
import com.kemai.app.util.Dates
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomerEditorSheet(
    repo: AppRepository,
    customerId: String?,
    defaultReferrerId: String?,
    defaultSiblingOf: String?,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    val labels = fieldLabels
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val existing: Customer? = remember(customerId) { repo.data.customers.firstOrNull { it.id == customerId } }
    val isNew = existing == null

    // 新建时的默认介绍人：加下级 → 当前客户；加同级 → 当前客户的介绍人
    val initialReferrer: String? = remember(customerId, defaultReferrerId, defaultSiblingOf) {
        if (existing != null) existing.referrerId
        else if (defaultSiblingOf != null) repo.data.customers.firstOrNull { it.id == defaultSiblingOf }?.referrerId
        else defaultReferrerId
    }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var cardDate by remember { mutableStateOf(existing?.firstCardDate) }
    var productIds by remember { mutableStateOf(existing?.productIds ?: emptyList()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var entries by remember { mutableStateOf(existing?.customEntries ?: emptyList()) }
    var referrerId by remember { mutableStateOf(initialReferrer) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showReferrerPicker by remember { mutableStateOf(false) }
    var productQuery by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    val workId = existing?.id ?: "new"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 28.dp),
        ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isNew) "新建客户" else "编辑客户",
                    style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                    color = pal.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    KemaiIcon(R.drawable.ic_close, contentDescription = "关闭", tint = pal.textSecondary, modifier = Modifier.size(20.dp))
                }
            }

            // 客户名称
            SectionLabel("${labels.name} *")
            KemaiTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                placeholder = "例如：张三",
            )
            if (nameError) {
                Text(
                    text = "${labels.name}不能为空",
                    style = TextStyle(fontSize = d.caption),
                    color = Color(0xFFB3261E),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 首次办卡时间
            SectionLabel(labels.cardDate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.surfaceVariant)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (cardDate.isNullOrBlank()) "未填写" else Dates.humanize(cardDate),
                    style = TextStyle(fontSize = d.body),
                    color = if (cardDate.isNullOrBlank()) pal.textSecondary.copy(alpha = 0.7f) else pal.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (!cardDate.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { cardDate = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        KemaiIcon(R.drawable.ic_close, contentDescription = "清除", tint = pal.textSecondary, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                KemaiIcon(R.drawable.ic_calendar, contentDescription = null, tint = pal.textSecondary, modifier = Modifier.size(18.dp))
            }

            // 使用过的产品
            SectionLabel(labels.product)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repo.data.products
                    .filter { !it.archived || it.id in productIds }
                    .sortedBy { it.order }
                    .forEach { p ->
                        val sel = p.id in productIds
                        SelectableChip(
                            text = if (p.archived) "${p.name}（停用）" else p.name,
                            selected = sel,
                            onClick = {
                                productIds = if (sel) productIds - p.id else productIds + p.id
                            },
                        )
                    }
                if (repo.data.products.none { !it.archived }) {
                    Text(
                        text = "还没有产品，在下面输入即可新建",
                        style = TextStyle(fontSize = d.caption),
                        color = pal.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            KemaiTextField(
                value = productQuery,
                onValueChange = { productQuery = it },
                placeholder = "搜索或输入新产品名…",
                trailing = {
                    if (productQuery.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .background(pal.accent.copy(alpha = 0.15f))
                                .clickable {
                                    val p = repo.addProduct(productQuery)
                                    if (p != null && p.id !in productIds) productIds = productIds + p.id
                                    productQuery = ""
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            KemaiIcon(R.drawable.ic_add, contentDescription = "新建产品", tint = pal.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                },
            )
            // 产品搜索建议
            if (productQuery.isNotBlank()) {
                val matches = repo.data.products
                    .filter { !it.archived && it.name.contains(productQuery, ignoreCase = true) && it.id !in productIds }
                if (matches.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        matches.take(8).forEach { p ->
                            SelectableChip(text = p.name, selected = false, onClick = {
                                productIds = productIds + p.id
                                productQuery = ""
                            })
                        }
                    }
                }
            }

            // 介绍人
            SectionLabel("介绍人")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal.surfaceVariant)
                    .clickable { showReferrerPicker = true }
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val refName = repo.data.customers.firstOrNull { it.id == referrerId }?.name
                Text(
                    text = refName ?: "无（根客户）",
                    style = TextStyle(fontSize = d.body),
                    color = if (refName == null) pal.textSecondary else pal.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(text = "▾", style = TextStyle(fontSize = d.body), color = pal.textSecondary)
            }

            // 备注
            SectionLabel("备注")
            KemaiTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = "随手记点什么，例如：周一上午不要打电话",
                singleLine = false,
                minHeight = 72,
            )

            // 自定义信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("自定义信息")
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { entries = entries + CustomEntry(id = newId("e")) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KemaiIcon(R.drawable.ic_add, contentDescription = null, tint = pal.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("添加", style = TextStyle(fontSize = d.caption, fontWeight = FontWeight.Medium), color = pal.accent)
                }
            }
            if (entries.isEmpty()) {
                Text(
                    text = "想给这位客户多记点信息？点「添加」，例如「微信号 / 同手机号」",
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            entries.forEachIndexed { index, entry ->
                CustomEntryRow(
                    entry = entry,
                    suggestions = repo.knownLabels().filter { it != entry.label },
                    onChange = { updated ->
                        entries = entries.toMutableList().also { it[index] = updated }
                    },
                    onRemove = {
                        entries = entries.toMutableList().also { it.removeAt(index) }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(22.dp))

            // 底部按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onRequestDelete(existing!!.id) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KemaiIcon(R.drawable.ic_delete, contentDescription = null, tint = Color(0xFFB3261E), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("删除", style = TextStyle(fontSize = d.body), color = Color(0xFFB3261E))
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.accent)
                        .clickable {
                            val trimmed = name.trim()
                            if (trimmed.isEmpty()) {
                                nameError = true
                                return@clickable
                            }
                            if (isNew) {
                                val c = repo.addCustomer(trimmed, referrerId, cardDate)
                                repo.updateCustomer(
                                    c.copy(
                                        productIds = productIds,
                                        note = note,
                                        customEntries = entries.filter { it.label.isNotBlank() || it.value.isNotBlank() },
                                    )
                                )
                                onSaved(c.id)
                            } else {
                                repo.updateCustomer(
                                    existing!!.copy(
                                        name = trimmed,
                                        firstCardDate = cardDate,
                                        productIds = productIds,
                                        note = note,
                                        customEntries = entries.filter { it.label.isNotBlank() || it.value.isNotBlank() },
                                        referrerId = referrerId,
                                    )
                                )
                                onSaved(existing.id)
                            }
                        }
                        .padding(horizontal = 34.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "保存",
                        style = TextStyle(fontSize = d.body, fontWeight = FontWeight.SemiBold),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    // ---------------- 日期选择 ----------------
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = isoToMillis(cardDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    cardDate = millisToIso(state.selectedDateMillis)
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state, title = { KemaiDatePickerTitle() })
        }
    }

    // ---------------- 介绍人选择 ----------------
    if (showReferrerPicker) {
        ReferrerPickerDialog(
            repo = repo,
            customerId = workId,
            current = referrerId,
            onPick = { referrerId = it; showReferrerPicker = false },
            onDismiss = { showReferrerPicker = false },
        )
    }
}

@Composable
private fun CustomEntryRow(
    entry: CustomEntry,
    suggestions: List<String>,
    onChange: (CustomEntry) -> Unit,
    onRemove: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    var labelFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(96.dp)) {
                KemaiTextField(
                    value = entry.label,
                    onValueChange = { onChange(entry.copy(label = it)); labelFocused = true },
                    placeholder = "标签",
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                KemaiTextField(
                    value = entry.value,
                    onValueChange = { onChange(entry.copy(value = it)) },
                    placeholder = "内容",
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(R.drawable.ic_close, contentDescription = "删除该条", tint = pal.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        // 标签自动补全：输过一次，下次就能选
        if (labelFocused && entry.label.isNotBlank()) {
            val hits = suggestions.filter { it.contains(entry.label, ignoreCase = true) && it != entry.label }
            if (hits.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    hits.take(6).forEach { s ->
                        SelectableChip(text = s, selected = false, onClick = {
                            onChange(entry.copy(label = s))
                            labelFocused = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferrerPickerDialog(
    repo: AppRepository,
    customerId: String,
    current: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    var query by remember { mutableStateOf("") }

    val candidates = remember(query, customerId) {
        repo.data.customers
            .filter { it.id != customerId }
            .filter { repo.canSetReferrer(customerId, it.id) }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.surface,
        title = { Text("选择介绍人", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
        text = {
            Column {
                KemaiTextField(value = query, onValueChange = { query = it }, placeholder = "搜索客户…")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PickerItem(text = "无（作为根客户）", selected = current == null) { onPick(null) }
                    candidates.forEach { c ->
                        PickerItem(text = c.name, selected = c.id == current) { onPick(c.id) }
                    }
                    if (candidates.isEmpty()) {
                        Text(
                            text = "没有可选的客户",
                            style = TextStyle(fontSize = d.caption),
                            color = pal.textSecondary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PickerItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val pal = palette
    val d = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = d.body),
            color = if (selected) pal.accent else pal.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            KemaiIcon(R.drawable.ic_check, contentDescription = null, tint = pal.accent, modifier = Modifier.size(17.dp))
        }
    }
}

// ---------------- 日期转换（DatePicker 用 UTC 毫秒） ----------------

/**
 * 日期选择器的标题。
 * Material3 自带的标题是库里的英文字符串，资源语言不一定跟着应用走，
 * 所以这里直接自己给标题，保证和界面语言一致。
 */
@Composable
internal fun KemaiDatePickerTitle() {
    val d = LocalDimens.current
    val pal = palette
    val lang = com.kemai.app.ui.theme.LocalLanguage.current
    Text(
        text = if (lang == com.kemai.app.model.AppLanguage.EN) "Select date" else "选择日期",
        style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
        color = pal.textPrimary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun isoToMillis(iso: String?): Long? {
    val d = Dates.parse(iso) ?: return null
    return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun millisToIso(millis: Long?): String? {
    if (millis == null) return null
    return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
}
