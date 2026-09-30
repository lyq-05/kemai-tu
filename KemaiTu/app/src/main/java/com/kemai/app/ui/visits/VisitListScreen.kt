package com.kemai.app.ui.visits

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.kemai.app.R
import com.kemai.app.data.AppRepository
import com.kemai.app.model.VisitItem
import com.kemai.app.model.VisitList
import com.kemai.app.model.itemsOf
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.components.KemaiTextField
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.palette
import com.kemai.app.util.Dates

/**
 * 拜访清单页（提醒版）。
 *
 * 顶部是系统自动生成的「新客户每周拜访」，下面是你自建的清单。
 * 点左边的圆圈＝快速标记已拜访（用今天作为实际完成日期）；点整行＝打开详情编辑。
 */
@Composable
fun VisitListScreen(
    repo: AppRepository,
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onOpenReminderSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    val d = LocalDimens.current

    var editingItem by remember { mutableStateOf<VisitItem?>(null) }
    var addingTo by remember { mutableStateOf<VisitList?>(null) }
    var renaming by remember { mutableStateOf<VisitList?>(null) }
    var deleting by remember { mutableStateOf<VisitList?>(null) }
    var menuFor by remember { mutableStateOf<VisitList?>(null) }
    var creating by remember { mutableStateOf(false) }

    val lists = repo.data.visitLists.sortedBy { it.order }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pal.canvasBg),
    ) {
        // ---------- 顶栏 ----------
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
                text = "拜访清单",
                style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                color = pal.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { creating = true },
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(R.drawable.ic_add, contentDescription = "新建清单", tint = pal.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (lists.isEmpty()) {
                Text(
                    text = "还没有清单",
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary,
                    modifier = Modifier.padding(vertical = 30.dp),
                )
            }

            lists.forEachIndexed { index, list ->
                val items = repo.data.itemsOf(list.id)
                val pending = items.count { !it.done }
                ListCard(
                    list = list,
                    items = items,
                    pending = pending,
                    accent = if (list.builtin) pal.accent else pal.subtree(index + 1),
                    onMenu = { menuFor = list },
                    onAdd = { addingTo = list },
                    onToggleDone = { item ->
                        if (item.done) repo.unmarkVisited(item.id)
                        else repo.markVisited(item.id, Dates.todayIso(), item.content)
                    },
                    onOpen = { editingItem = it },
                    onOpenCustomer = onOpenCustomer,
                )
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------- 条目详情 ----------
    editingItem?.let { item ->
        val fresh = repo.data.visitItems.firstOrNull { it.id == item.id }
        if (fresh == null) {
            editingItem = null
        } else {
            VisitItemSheet(
                repo = repo,
                item = fresh,
                onDismiss = { editingItem = null },
                onOpenCustomer = { id ->
                    editingItem = null
                    onOpenCustomer(id)
                },
            )
        }
    }

    // ---------- 添加待拜访 ----------
    addingTo?.let { list ->
        AddVisitItemSheet(
            repo = repo,
            listName = list.name,
            allowPickExisting = true,
            onDismiss = { addingTo = null },
            onConfirm = { customerId, name, planned, content ->
                repo.addVisitItem(list.id, customerId, name, planned, content)
                addingTo = null
            },
        )
    }

    // ---------- 新建清单 ----------
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = pal.surface,
            title = { Text("新建清单", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = {
                Column {
                    Text(
                        text = "清单名字随你起，比如「本月重点」「老客户回访」。",
                        style = TextStyle(fontSize = d.caption),
                        color = pal.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    KemaiTextField(value = name, onValueChange = { name = it }, placeholder = "清单名称")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        repo.addVisitList(name)
                        creating = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } },
        )
    }

    // ---------- 重命名 ----------
    renaming?.let { list ->
        var name by remember(list.id) { mutableStateOf(list.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = pal.surface,
            title = { Text("重命名清单", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = { KemaiTextField(value = name, onValueChange = { name = it }, placeholder = "清单名称") },
            confirmButton = {
                TextButton(onClick = {
                    repo.renameVisitList(list.id, name)
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }

    // ---------- 删除清单 ----------
    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = pal.surface,
            title = { Text("删除「${list.name}」？", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    text = "清单和里面的 ${repo.data.itemsOf(list.id).size} 条待拜访会一起删掉，不可恢复。",
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteVisitList(list.id)
                    deleting = null
                }) { Text("确认删除", color = Color(0xFFB3261E)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    // ---------- 清单菜单 ----------
    menuFor?.let { list ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = pal.surface,
            title = { Text(list.name, style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = {
                Column {
                    if (list.builtin) {
                        Text(
                            text = "系统按「首次办卡时间」自动生成这份名单，每周一重置本周的已拜访状态。",
                            style = TextStyle(fontSize = d.caption),
                            color = pal.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        MenuLine("提醒设置") { menuFor = null; onOpenReminderSettings() }
                    } else {
                        MenuLine("重命名") { renaming = list; menuFor = null }
                        MenuLine("删除清单", danger = true) { deleting = list; menuFor = null }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun MenuLine(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val pal = palette
    val d = LocalDimens.current
    Text(
        text = text,
        style = TextStyle(fontSize = d.body),
        color = if (danger) Color(0xFFB3261E) else pal.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 13.dp, horizontal = 6.dp),
    )
}

@Composable
private fun ListCard(
    list: VisitList,
    items: List<VisitItem>,
    pending: Int,
    accent: Color,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
    onToggleDone: (VisitItem) -> Unit,
    onOpen: (VisitItem) -> Unit,
    onOpenCustomer: (String) -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(pal.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // 清单头
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(4.dp, 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = list.name,
                style = TextStyle(fontSize = d.body, fontWeight = FontWeight.SemiBold),
                color = pal.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (pending > 0) "$pending 位待拜访" else "全部完成",
                style = TextStyle(fontSize = d.caption),
                color = if (pending > 0) accent else pal.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(R.drawable.ic_add, contentDescription = "添加待拜访", tint = pal.textSecondary, modifier = Modifier.size(17.dp))
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onMenu() },
                contentAlignment = Alignment.Center,
            ) {
                KemaiIcon(R.drawable.ic_more, contentDescription = "更多", tint = pal.textSecondary, modifier = Modifier.size(18.dp))
            }
        }

        if (items.isEmpty()) {
            Text(
                text = if (list.builtin) "最近没有符合条件的新客户（首次办卡时间在设定范围内）"
                else "还没有待拜访客户，点右上角 ＋ 添加",
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
            )
            return@Column
        }

        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            VisitRow(
                item = item,
                accent = accent,
                onToggleDone = { onToggleDone(item) },
                onOpen = { onOpen(item) },
                onOpenCustomer = onOpenCustomer,
            )
        }
    }
}

@Composable
private fun VisitRow(
    item: VisitItem,
    accent: Color,
    onToggleDone: () -> Unit,
    onOpen: () -> Unit,
    onOpenCustomer: (String) -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen() }
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 打勾圈：快速标记/取消已拜访
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (item.done) accent else pal.surfaceVariant)
                .clickable { onToggleDone() },
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) {
                KemaiIcon(R.drawable.ic_check, contentDescription = "取消已拜访", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.customerName.ifBlank { "未命名" },
                    style = TextStyle(
                        fontSize = d.body,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = if (item.done) pal.textSecondary else pal.textPrimary,
                    modifier = if (item.customerId != null) {
                        Modifier.clickable { onOpenCustomer(item.customerId) }
                    } else {
                        Modifier
                    },
                )
                if (item.done) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "已拜访 ${Dates.shortHuman(item.completedDate)}",
                        style = TextStyle(fontSize = d.tiny),
                        color = accent,
                    )
                }
            }
            val sub = buildList {
                item.plannedDate?.takeIf { it.isNotBlank() }?.let { add("计划 ${Dates.relative(it)}") }
                item.content.takeIf { it.isNotBlank() }?.let { add(it.take(24)) }
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textSecondary,
                    maxLines = 1,
                )
            }
        }

        if (item.customerId == null) {
            Text(
                text = "手输",
                style = TextStyle(fontSize = d.tiny),
                color = pal.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(pal.surfaceVariant)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}
