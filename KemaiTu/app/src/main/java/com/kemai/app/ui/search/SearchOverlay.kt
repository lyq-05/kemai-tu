package com.kemai.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kemai.app.R
import com.kemai.app.data.AppRepository
import com.kemai.app.model.Customer
import com.kemai.app.model.pathTo
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.fieldLabels
import com.kemai.app.ui.theme.palette

/**
 * 搜索：匹配客户名称、产品、备注、自定义条目。
 * 点结果 → 画布飞到该节点并高亮（折叠路径会自动展开）。
 */
@Composable
fun SearchOverlay(
    repo: AppRepository,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    onCreateNew: (String) -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val results = remember(query, repo.data.customers) {
        if (query.isBlank()) emptyList()
        else repo.data.customers.filter { matches(repo, it, query) }
            .sortedBy { it.name }
            .take(60)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pal.canvasBg)
            .statusBarsPadding(),
    ) {
        // 搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KemaiIcon(R.drawable.ic_search, contentDescription = null, tint = pal.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索客户、产品、备注…",
                            style = TextStyle(fontSize = d.body),
                            color = pal.textSecondary.copy(alpha = 0.7f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.merge(TextStyle(fontSize = d.body, color = pal.textPrimary)),
                        cursorBrush = SolidColor(pal.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        KemaiIcon(R.drawable.ic_close, contentDescription = "清空", tint = pal.textSecondary, modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "取消",
                style = TextStyle(fontSize = d.body),
                color = pal.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider.copy(alpha = 0.5f)))

        when {
            query.isBlank() -> {
                Text(
                    text = "输入关键词开始搜索\n可以搜客户名称、产品、备注、自定义信息",
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textSecondary,
                    modifier = Modifier.padding(20.dp),
                )
            }

            results.isEmpty() -> {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "没有找到匹配的客户",
                        style = TextStyle(fontSize = d.body),
                        color = pal.textSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(pal.accent)
                            .clickable { onCreateNew(query.trim()) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        Text(
                            text = "新建客户「${query.trim()}」",
                            style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { c ->
                        ResultRow(
                            name = c.name,
                            path = repo.data.pathTo(c.id).dropLast(1).joinToString(" › "),
                            snippet = matchSnippet(repo, c, query),
                            onClick = { onPick(c.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    name: String,
    path: String,
    snippet: String?,
    onClick: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = TextStyle(fontSize = d.body, fontWeight = FontWeight.Medium),
                color = pal.textPrimary,
            )
            if (path.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = path,
                    style = TextStyle(fontSize = d.caption),
                    color = pal.textSecondary,
                )
            }
        }
        if (snippet != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = snippet,
                style = TextStyle(fontSize = d.caption),
                color = pal.textSecondary,
                maxLines = 2,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal.divider.copy(alpha = 0.35f)))
}

// ---------------- 匹配逻辑 ----------------

private fun matches(repo: AppRepository, c: Customer, q: String): Boolean {
    if (c.name.contains(q, ignoreCase = true)) return true
    if (c.note.contains(q, ignoreCase = true)) return true
    if (c.customEntries.any { it.label.contains(q, true) || it.value.contains(q, true) }) return true
    if (c.firstCardDate?.contains(q) == true) return true
    return repo.data.products.any { it.id in c.productIds && it.name.contains(q, ignoreCase = true) }
}

private fun matchSnippet(repo: AppRepository, c: Customer, q: String): String? {
    if (c.name.contains(q, ignoreCase = true)) return null
    if (c.note.contains(q, ignoreCase = true)) return "备注：${snippet(c.note, q)}"
    c.customEntries.firstOrNull { it.label.contains(q, true) || it.value.contains(q, true) }?.let {
        return "${it.label}：${snippet(it.value, q)}"
    }
    val productNames = repo.data.products
        .filter { it.id in c.productIds && it.name.contains(q, ignoreCase = true) }
        .map { it.name }
    if (productNames.isNotEmpty()) return "产品：${productNames.joinToString("、")}"
    if (c.firstCardDate?.contains(q) == true) return "办卡时间：${c.firstCardDate}"
    return null
}

private fun snippet(text: String, q: String): String {
    val idx = text.indexOf(q, ignoreCase = true)
    if (idx < 0) return text.take(40)
    val start = (idx - 10).coerceAtLeast(0)
    val end = (idx + q.length + 24).coerceAtMost(text.length)
    return (if (start > 0) "…" else "") + text.substring(start, end) + (if (end < text.length) "…" else "")
}
