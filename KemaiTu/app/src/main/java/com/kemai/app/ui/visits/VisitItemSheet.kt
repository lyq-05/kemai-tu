package com.kemai.app.ui.visits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.kemai.app.model.VisitItem
import com.kemai.app.model.visitRecordsOf
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.components.KemaiTextField
import com.kemai.app.ui.components.SectionLabel
import com.kemai.app.ui.components.SelectableChip
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.palette
import com.kemai.app.util.Dates

/** 待拜访条目详情：看历史、改计划时间与内容、标记或取消已拜访 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitItemSheet(
    repo: AppRepository,
    item: VisitItem,
    onDismiss: () -> Unit,
    onOpenCustomer: (String) -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var plannedDate by remember(item.id) { mutableStateOf(item.plannedDate) }
    var content by remember(item.id) { mutableStateOf(item.content) }
    var completedDate by remember(item.id) { mutableStateOf(item.completedDate) }

    val history = remember(item.customerId, repo.data.visitRecords) {
        item.customerId?.let { repo.data.visitRecordsOf(it) }.orEmpty()
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.customerName.ifBlank { "未命名" },
                        style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                        color = pal.textPrimary,
                    )
                    if (item.customerId != null) {
                        Text(
                            text = "点这里跳到导图上的节点 ›",
                            style = TextStyle(fontSize = d.caption),
                            color = pal.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenCustomer(item.customerId) }
                                .padding(vertical = 3.dp),
                        )
                    } else {
                        Text(
                            text = "这是手动输入的客户，没有关联导图",
                            style = TextStyle(fontSize = d.caption),
                            color = pal.textSecondary,
                        )
                    }
                }
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

            SectionLabel("计划回访时间")
            DateRow(
                value = plannedDate,
                placeholder = "未安排",
                onValueChange = { plannedDate = it },
            )

            if (item.done) {
                SectionLabel("实际完成日期")
                DateRow(
                    value = completedDate,
                    placeholder = "未填写",
                    onValueChange = { picked ->
                        completedDate = picked
                        repo.updateVisitItem(
                            item.copy(
                                completedDate = picked,
                                plannedDate = plannedDate,
                                content = content,
                            )
                        )
                    },
                )
            }

            SectionLabel("回访内容")
            KemaiTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = "聊了什么、下一步怎么跟，随手记…",
                singleLine = false,
                minHeight = 76,
            )

            Spacer(Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (item.done) {
                                repo.unmarkVisited(item.id)
                            } else {
                                repo.markVisited(item.id, completedDate ?: Dates.todayIso(), content)
                            }
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Text(
                        text = if (item.done) "取消「已拜访」" else "标记已拜访",
                        style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
                        color = pal.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.accent)
                        .clickable {
                            repo.updateVisitItem(
                                item.copy(
                                    plannedDate = plannedDate,
                                    content = content,
                                    completedDate = completedDate,
                                )
                            )
                            onDismiss()
                        }
                        .padding(horizontal = 30.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "保存",
                        style = TextStyle(fontSize = d.body, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.customerId != null) {
                    Text(
                        text = "从自动清单移出",
                        style = TextStyle(fontSize = d.caption),
                        color = pal.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                repo.excludeFromAuto(item.customerId!!)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            repo.deleteVisitItem(item.id)
                            onDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KemaiIcon(R.drawable.ic_delete, contentDescription = null, tint = Color(0xFFB3261E), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除这一条", style = TextStyle(fontSize = d.caption), color = Color(0xFFB3261E))
                }
            }

            if (history.isNotEmpty()) {
                SectionLabel("这位客户的拜访历史")
                history.take(8).forEach { r ->
                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = Dates.shortHuman(r.completedDate),
                            style = TextStyle(fontSize = d.caption),
                            color = pal.accent,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            text = r.content.ifBlank { "（未记录内容）" },
                            style = TextStyle(fontSize = d.caption),
                            color = pal.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 往清单里加一条待拜访：可以从导图里挑，也可以手输导图外的名字 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVisitItemSheet(
    repo: AppRepository,
    listName: String,
    allowPickExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (customerId: String?, name: String, plannedDate: String?, content: String) -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pickFromMap by remember { mutableStateOf(allowPickExisting) }
    var query by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var chosenId by remember { mutableStateOf<String?>(null) }
    var chosenName by remember { mutableStateOf("") }
    var plannedDate by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    val matches = remember(query, repo.data.customers) {
        if (query.isBlank()) repo.data.customers.sortedBy { it.name }.take(30)
        else repo.data.customers.filter { it.name.contains(query, ignoreCase = true) }.take(30)
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "添加待拜访",
                        style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                        color = pal.textPrimary,
                    )
                    Text(
                        text = "加入「$listName」",
                        style = TextStyle(fontSize = d.caption),
                        color = pal.textSecondary,
                    )
                }
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

            SectionLabel("客户")
            if (allowPickExisting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip(text = "从导图里选", selected = pickFromMap, onClick = { pickFromMap = true })
                    SelectableChip(text = "手动输入", selected = !pickFromMap, onClick = { pickFromMap = false })
                }
                Spacer(Modifier.height(10.dp))
            }

            if (pickFromMap) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.surfaceVariant)
                        .clickable { showPicker = true }
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chosenName.ifBlank { "点击选择客户…" },
                        style = TextStyle(fontSize = d.body),
                        color = if (chosenName.isBlank()) pal.textSecondary.copy(alpha = 0.7f) else pal.textPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("▾", style = TextStyle(fontSize = d.body), color = pal.textSecondary)
                }
            } else {
                KemaiTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    placeholder = "客户名字（可以不在导图里）",
                )
            }

            SectionLabel("计划回访时间")
            DateRow(
                value = plannedDate,
                placeholder = "未安排",
                onValueChange = { plannedDate = it },
            )

            SectionLabel("回访内容")
            KemaiTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = "打算聊什么、注意什么…",
                singleLine = false,
                minHeight = 70,
            )

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal.accent)
                        .clickable {
                            val name = if (pickFromMap) chosenName else manualName.trim()
                            if (name.isNotBlank()) {
                                onConfirm(if (pickFromMap) chosenId else null, name, plannedDate, content)
                            }
                        }
                        .padding(horizontal = 30.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "添加",
                        style = TextStyle(fontSize = d.body, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = pal.surface,
            title = { Text("选择客户", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = {
                Column {
                    KemaiTextField(value = query, onValueChange = { query = it }, placeholder = "搜索客户…")
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        matches.forEach { c ->
                            Text(
                                text = c.name,
                                style = TextStyle(fontSize = d.body),
                                color = if (c.id == chosenId) pal.accent else pal.textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        chosenId = c.id
                                        chosenName = c.name
                                        showPicker = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                            )
                        }
                        if (matches.isEmpty()) {
                            Text(
                                text = "没有找到客户",
                                style = TextStyle(fontSize = d.caption),
                                color = pal.textSecondary,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("取消") } },
        )
    }
}

/** 统一的日期行：自己持有日期选择器的开关状态，选完直接回调 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    value: String?,
    placeholder: String,
    onValueChange: (String?) -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    var show by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(pal.surfaceVariant)
            .clickable { show = true }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (value.isNullOrBlank()) placeholder else Dates.humanize(value),
            style = TextStyle(fontSize = d.body),
            color = if (value.isNullOrBlank()) pal.textSecondary.copy(alpha = 0.7f) else pal.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (!value.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onValueChange(null) },
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(R.drawable.ic_close, contentDescription = "清除", tint = pal.textSecondary, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        KemaiIcon(R.drawable.ic_calendar, contentDescription = null, tint = pal.textSecondary, modifier = Modifier.size(18.dp))
    }

    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.isoToUtcMillis(value))
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(Dates.utcMillisToIso(state.selectedDateMillis))
                    show = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } },
        ) { DatePicker(state = state, title = { com.kemai.app.ui.editor.KemaiDatePickerTitle() }) }
    }
}
