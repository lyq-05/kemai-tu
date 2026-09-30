package com.kemai.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.kemai.app.model.Product
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.components.KemaiTextField
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.fieldLabels
import com.kemai.app.ui.theme.palette

@Composable
fun ProductScreen(
    repo: AppRepository,
    onBack: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current

    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Product?>(null) }
    var deleting by remember { mutableStateOf<Product?>(null) }
    var menuFor by remember { mutableStateOf<Product?>(null) }

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
                text = "产品库",
                style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
                color = pal.textPrimary,
            )
        }

        Text(
            text = "在客户里勾选的产品都来自这里。删除产品不会丢掉已有记录 —— "
                + "该产品名会转成客户身上的一条自定义信息保留下来。",
            style = TextStyle(fontSize = d.caption),
            color = pal.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            val products = repo.data.products.sortedBy { it.order }
            if (products.isEmpty()) {
                Text(
                    text = "还没有产品，在下面添加第一个吧",
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary,
                    modifier = Modifier.padding(vertical = 26.dp),
                )
            }
            products.forEachIndexed { index, p ->
                ProductRow(
                    product = p,
                    usage = repo.productUsageCount(p.id),
                    canUp = index > 0,
                    canDown = index < products.lastIndex,
                    onUp = {
                        val list = products.toMutableList()
                        list.add(index - 1, list.removeAt(index))
                        repo.reorderProducts(list)
                    },
                    onDown = {
                        val list = products.toMutableList()
                        list.add(index + 1, list.removeAt(index))
                        repo.reorderProducts(list)
                    },
                    onMenu = { menuFor = p },
                )
            }
            Spacer(Modifier.height(80.dp))
        }

        // 底部新增
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(pal.surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                KemaiTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "新增产品名称…",
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (newName.isBlank()) pal.surfaceVariant else pal.accent)
                    .clickable {
                        if (newName.isNotBlank()) {
                            repo.addProduct(newName)
                            newName = ""
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                KemaiIcon(
                    id = R.drawable.ic_add,
                    contentDescription = "新增产品",
                    tint = if (newName.isBlank()) pal.textSecondary
                    else androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    // 重命名
    renaming?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = pal.surface,
            title = { Text("重命名产品", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = { KemaiTextField(value = text, onValueChange = { text = it }, placeholder = "产品名称") },
            confirmButton = {
                TextButton(onClick = {
                    repo.renameProduct(p.id, text)
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }

    // 删除确认
    deleting?.let { p ->
        val usage = repo.productUsageCount(p.id)
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = pal.surface,
            title = { Text("删除「${p.name}」？", style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    text = if (usage > 0)
                        "有 $usage 位客户正在使用这个产品。\n删除后，这些客户的产品标签会转成一条自定义信息保留，记录不会丢失。"
                    else "删除后不可恢复。",
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteProduct(p.id)
                    deleting = null
                }) { Text("确认删除", color = Color(0xFFB3261E)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }

    // 更多操作
    menuFor?.let { p ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = pal.surface,
            title = { Text(p.name, style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold)) },
            text = {
                Column {
                    MenuLine("重命名") { renaming = p; menuFor = null }
                    MenuLine(if (p.archived) "重新启用" else "停用（不出现在选择列表，历史保留）") {
                        repo.setProductArchived(p.id, !p.archived)
                        menuFor = null
                    }
                    MenuLine("删除", danger = true) { deleting = p; menuFor = null }
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
private fun ProductRow(
    product: Product,
    usage: Int,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onMenu: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = TextStyle(
                    fontSize = d.body,
                    fontWeight = if (product.archived) FontWeight.Normal else FontWeight.Medium,
                ),
                color = if (product.archived) pal.textSecondary else pal.textPrimary,
            )
            Text(
                text = buildString {
                    append("$usage 位客户在用")
                    if (product.archived) append(" · 已停用")
                },
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
            )
        }
        ArrowButton("↑", enabled = canUp, onClick = onUp)
        Spacer(Modifier.width(4.dp))
        ArrowButton("↓", enabled = canDown, onClick = onDown)
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(50))
                .clickable { onMenu() },
            contentAlignment = Alignment.Center,
        ) {
            KemaiIcon(R.drawable.ic_more, contentDescription = "更多", tint = pal.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ArrowButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val pal = palette
    val d = LocalDimens.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) pal.surfaceVariant else Color.Transparent)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = TextStyle(fontSize = d.body),
            color = if (enabled) pal.textPrimary else pal.textSecondary.copy(alpha = 0.3f),
        )
    }
}
