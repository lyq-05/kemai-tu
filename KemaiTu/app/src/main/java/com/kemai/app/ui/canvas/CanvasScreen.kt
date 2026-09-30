package com.kemai.app.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kemai.app.R
import com.kemai.app.data.AppRepository
import com.kemai.app.model.ColorMode
import com.kemai.app.model.Customer
import com.kemai.app.ui.components.KemaiIcon
import com.kemai.app.ui.theme.KemaiPalette
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.layoutMode
import com.kemai.app.ui.theme.lerpColor
import com.kemai.app.ui.theme.palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val MIN_SCALE = 0.30f
private const val MAX_SCALE = 2.50f

/** 画布外部控制器：让顶部/底部按钮能命令画布「适应全图」「飞到某节点」 */
class CanvasController {
    internal var fitSignal by mutableIntStateOf(0)
        private set
    internal var focusTarget by mutableStateOf<String?>(null)
        private set
    internal var focusSignal by mutableIntStateOf(0)
        private set

    fun fitAll() {
        fitSignal++
    }

    fun focus(customerId: String) {
        focusTarget = customerId
        focusSignal++
    }
}

private data class NodeMetric(val width: Float, val text: TextLayoutResult)

private data class LinkVisual(val path: Path, val bounds: Rect, val color: Color)

private data class NodeCount(val children: Int, val descendants: Int)

internal fun badgeLabel(customer: Customer, childCount: Int, descendantCount: Int): String =
    if (customer.collapsed) "$descendantCount 位已收起" else "$childCount 位下级"

@Composable
fun CustomerCanvas(
    repo: AppRepository,
    controller: CanvasController,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onOpenEditor: (String) -> Unit,
    onOverflowMenu: (String) -> Unit,
    onCreateFirst: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    val d = LocalDimens.current
    val mode = layoutMode
    val colorMode = com.kemai.app.ui.theme.colorMode
    val byDepth = colorMode == ColorMode.DEPTH
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    // ---------- dp → px ----------
    val nodeHeight = with(density) { 44.dp.toPx() }
    val cardCorner = with(density) { 12.dp.toPx() }
    val hPad = with(density) { 14.dp.toPx() }
    val minCard = with(density) { 88.dp.toPx() }
    val maxCard = with(density) { 200.dp.toPx() }
    val maxText = with(density) { 168.dp.toPx() }
    val siblingGap = with(density) { 24.dp.toPx() }
    val levelGap = with(density) { 64.dp.toPx() }
    val rootGap = with(density) { 96.dp.toPx() }
    val badgeGap = with(density) { 5.dp.toPx() }
    val badgeHeight = with(density) { 22.dp.toPx() }
    val badgeHPad = with(density) { 9.dp.toPx() }
    val minTouch = with(density) { 44.dp.toPx() }
    val barW = with(density) { 3.dp.toPx() }
    val gridBase = with(density) { 26.dp.toPx() }
    val contentPad = with(density) { 56.dp.toPx() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var didInitialFit by remember { mutableStateOf(false) }

    // ---------- 1. 一次性量好所有文字 ----------
    val nameStyle = remember(d.scale) {
        TextStyle(fontSize = d.nodeTitle, fontWeight = FontWeight.Medium)
    }
    val badgeStyle = remember(d.scale) {
        TextStyle(fontSize = d.caption, fontWeight = FontWeight.Medium)
    }

    // 直接下级 / 全部子孙数量，供折叠按钮文案使用（只算一次，避免 O(n²)）
    val counts: Map<String, NodeCount> = remember(repo.data.customers) {
        val childrenMap = repo.data.customers
            .filter { it.referrerId != null }
            .groupBy { it.referrerId!! }
        repo.data.customers.associate { c ->
            val kids = childrenMap[c.id].orEmpty()
            var total = 0
            val seen = HashSet<String>()
            val stack = ArrayDeque<String>()
            kids.forEach { stack.addLast(it.id) }
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                if (!seen.add(cur)) continue
                total++
                childrenMap[cur].orEmpty().forEach { stack.addLast(it.id) }
            }
            c.id to NodeCount(kids.size, total)
        }
    }

    val nameMetrics: Map<String, NodeMetric> = remember(repo.data.customers, nameStyle) {
        repo.data.customers.associate { c ->
            val label = c.name.ifBlank { "未命名客户" }
            val natural = measurer.measure(
                text = AnnotatedString(label),
                style = nameStyle,
                maxLines = 1,
                softWrap = false,
            )
            val textW = min(natural.size.width.toFloat(), maxText)
            val cardW = (textW + hPad * 2f).coerceIn(minCard, maxCard)
            val laid = if (natural.size.width > maxText) {
                measurer.measure(
                    text = AnnotatedString(label),
                    style = nameStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false,
                    constraints = Constraints(maxWidth = maxText.toInt()),
                )
            } else {
                natural
            }
            c.id to NodeMetric(cardW, laid)
        }
    }

    val badgeMetrics: Map<String, TextLayoutResult> = remember(repo.data.customers, badgeStyle, counts) {
        repo.data.customers.associate { c ->
            val n = counts[c.id] ?: NodeCount(0, 0)
            val label = badgeLabel(c, n.children, n.descendants)
            c.id to measurer.measure(
                text = AnnotatedString(label),
                style = badgeStyle,
                maxLines = 1,
                softWrap = false,
            )
        }
    }

    // ---------- 2. 布局 ----------
    val layout = remember(repo.data, nameMetrics, badgeMetrics, d.scale, mode) {
        TreeLayoutEngine(
            mode = mode,
            nodeHeight = nodeHeight,
            siblingGap = siblingGap,
            levelGap = levelGap,
            rootGap = rootGap,
            badgeGap = badgeGap,
            badgeHeight = badgeHeight,
        ).compute(
            data = repo.data,
            widthOf = { c -> nameMetrics[c.id]?.width ?: minCard },
            badgeWidthOf = { c ->
                (badgeMetrics[c.id]?.size?.width?.toFloat() ?: 40f) + badgeHPad * 2f + badgeHeight
            },
        )
    }

    val currentLayout by rememberUpdatedState(layout)
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)

    /**
     * 连线的 Path 预先算好并缓存。
     * 连线画在画布坐标系里，平移缩放只是外层 transform，几何本身不变 ——
     * 所以没必要每帧重新算 180 条贝塞尔折线。
     */
    val linkVisuals: List<LinkVisual> = remember(layout, pal, colorMode) {
        layout.links.mapNotNull { link ->
            val from = layout.nodeById(link.fromId) ?: return@mapNotNull null
            val path = roundedPolyline(link.points, 14f)
            LinkVisual(
                path = path,
                bounds = path.getBounds(),
                color = pal.linkColor(from.depth, from.rootIndex, colorMode == ColorMode.DEPTH),
            )
        }
    }

    fun fitNow() {
        val l = currentLayout
        if (viewport.width <= 0f || viewport.height <= 0f || l.isEmpty) return
        val b = l.bounds
        val sx = (viewport.width - contentPad * 2f) / max(b.width, 1f)
        val sy = (viewport.height - contentPad * 2f) / max(b.height, 1f)
        val s = min(sx, sy).coerceIn(MIN_SCALE, 1f)
        scale = s
        offset = Offset(
            (viewport.width - b.width * s) / 2f - b.left * s,
            (viewport.height - b.height * s) / 2f - b.top * s,
        )
    }

    LaunchedEffect(viewport, layout.isEmpty, controller.fitSignal) {
        if (viewport.width > 0f && !layout.isEmpty) {
            if (!didInitialFit || controller.fitSignal > 0) {
                didInitialFit = true
                fitNow()
            }
        }
    }

    LaunchedEffect(controller.focusSignal) {
        if (controller.focusSignal == 0) return@LaunchedEffect
        val node = currentLayout.nodeById(controller.focusTarget) ?: return@LaunchedEffect
        val s = currentScale.coerceAtLeast(0.95f)
        scale = s
        offset = Offset(
            viewport.width / 2f - node.rect.center.x * s,
            viewport.height / 2f - node.rect.center.y * s,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pal.canvasBg)
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                // 注意：这里**故意不注册 onDoubleTap**。
                // 一旦注册了双击，detectTapGestures 就必须为每次单击等一个
                // 双击超时（约 300ms）才能确定"不是双击"，选中、折叠这些操作
                // 全都会拖上半拍 —— 真机上手感很明显。
                // 编辑卡片走底部操作条的「编辑」，或长按弹出的菜单，功能不丢。
                detectTapGestures(
                    onTap = { pos ->
                        val p = (pos - currentOffset) / currentScale
                        val badgeHit = badgeTouchAt(currentLayout, p, minTouch)
                        if (badgeHit != null) {
                            repo.toggleCollapse(badgeHit.customer.id)
                        } else {
                            onSelect(currentLayout.nodeAt(p)?.customer?.id)
                        }
                    },
                    onLongPress = { pos ->
                        val p = (pos - currentOffset) / currentScale
                        currentLayout.nodeAt(p)?.let {
                            onSelect(it.customer.id)
                            onOverflowMenu(it.customer.id)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val s0 = currentScale
                    val newScale = (s0 * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val k = newScale / s0
                    val o0 = currentOffset
                    offset = centroid + pan - (centroid - o0) * k
                    scale = newScale
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGrid(pal, gridBase, scale, offset)

            val visible = Rect(
                left = -offset.x / scale,
                top = -offset.y / scale,
                right = (size.width - offset.x) / scale,
                bottom = (size.height - offset.y) / scale,
            )

            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                val strokeW = (1.8f * scale).coerceAtLeast(1.0f)
                for (lv in linkVisuals) {
                    if (!lv.bounds.overlaps(visible)) continue
                    drawPath(
                        path = lv.path,
                        color = lv.color,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                // 细节分级：卡片在屏幕上越小，画得越省。
                // 阈值按「文字还看不看得清」定：16sp 的标题缩到 8sp 以下就没有可读性了，
                // 这时候画文字纯属浪费，不如把预算留给可见节点的绘制。
                val screenCardHeight = nodeHeight * scale
                val detail = when {
                    screenCardHeight < 18f -> 0
                    screenCardHeight < 42f -> 1
                    else -> 2
                }
                drawNodes(
                    layout = layout,
                    nameMetrics = nameMetrics,
                    badgeMetrics = badgeMetrics,
                    pal = pal,
                    selectedId = selectedId,
                    visible = visible,
                    barW = barW,
                    cardCorner = cardCorner,
                    scale = scale,
                    detail = detail,
                    byDepth = byDepth,
                )
            }
        }

        if (layout.isEmpty) {
            EmptyHint(
                onCreateFirst = onCreateFirst,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

// ==================== 绘制 ====================

/**
 * 折叠按钮的命中测试。
 * 视觉上按钮只有 22dp 高，但触摸目标会撑到 44dp：多出来的部分 35% 向上、65% 向下，
 * 这样既吃掉了卡片和按钮之间那点空隙（避免手指点在缝里「点了没反应」），
 * 又不会侵占卡片本身的点击区域。
 */
private fun badgeTouchAt(layout: LayoutResult, p: Offset, minTouch: Float): LayoutNode? =
    layout.nodes.lastOrNull { n ->
        val r = n.badgeRect ?: return@lastOrNull false
        val extra = (minTouch - r.height).coerceAtLeast(0f)
        Rect(
            left = r.left - 6f,
            top = r.top - extra * 0.35f,
            right = r.right + 6f,
            bottom = r.bottom + extra * 0.65f,
        ).contains(p)
    }

private fun DrawScope.drawGrid(
    pal: KemaiPalette,
    baseStep: Float,
    scale: Float,
    offset: Offset,
) {
    var step = baseStep * scale
    // 自适应密度：无论怎么缩放，点阵数量都控制在一个稳定范围
    while (step < 30f) step *= 2f
    while (step > 96f) step /= 2f

    val startX = offset.x.mod(step)
    val startY = offset.y.mod(step)
    val dotR = (1.15f * scale).coerceIn(0.85f, 2.4f)

    val points = ArrayList<Offset>(512)
    var y = startY
    while (y < size.height + step) {
        var x = startX
        while (x < size.width + step) {
            points += Offset(x, y)
            x += step
        }
        y += step
    }
    if (points.isNotEmpty()) {
        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = pal.gridDot,
            strokeWidth = dotR * 2f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawNodes(
    layout: LayoutResult,
    nameMetrics: Map<String, NodeMetric>,
    badgeMetrics: Map<String, TextLayoutResult>,
    pal: KemaiPalette,
    selectedId: String?,
    visible: Rect,
    barW: Float,
    cardCorner: Float,
    scale: Float,
    /** 2 = 完整, 1 = 无文字无角标, 0 = 只画一个色块 */
    detail: Int,
    /** true = 按脉络深度配色，false = 按客户家族配色 */
    byDepth: Boolean,
) {
    for (node in layout.nodes) {
        if (!node.rect.overlaps(visible)) continue

        val selected = node.customer.id == selectedId
        val accent = pal.nodeAccent(node.depth, node.rootIndex, byDepth)
        val baseFill = pal.nodeFill(node.depth, node.rootIndex, byDepth)

        // 选中态要一眼看得出来：放大 + 更粗描边 + 外圈光晕 + 填充向主题色偏移
        val growX = if (selected && detail > 0) node.rect.width * 0.03f else 0f
        val growY = if (selected && detail > 0) node.rect.height * 0.06f else 0f
        val rect = Rect(
            node.rect.left - growX,
            node.rect.top - growY,
            node.rect.right + growX,
            node.rect.bottom + growY,
        )
        val fill = if (selected) lerpColor(baseFill, accent, 0.22f) else baseFill

        if (detail == 0) {
            // 缩得很小时只留色块，靠色相仍能看清整体脉络
            drawRoundRect(
                color = if (selected) accent else fill,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(cardCorner, cardCorner),
            )
            continue
        }

        if (selected) {
            // 外圈光晕：即便在密集的图里也能一眼锁定选中的是哪个
            val glow = (7f * scale).coerceAtLeast(3.5f)
            drawRoundRect(
                color = accent.copy(alpha = 0.32f),
                topLeft = Offset(rect.left - glow, rect.top - glow),
                size = Size(rect.width + glow * 2f, rect.height + glow * 2f),
                cornerRadius = CornerRadius(cardCorner + glow, cardCorner + glow),
            )
        }

        if (node.isRoot && detail == 2) {
            drawRoundRect(
                color = accent,
                topLeft = Offset(rect.left + 7f, rect.top - 7f),
                size = Size(20f, 3f),
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
        }

        drawRoundRect(
            color = fill,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(cardCorner, cardCorner),
        )
        drawRoundRect(
            color = if (selected) accent else accent.copy(alpha = 0.38f),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(cardCorner, cardCorner),
            style = Stroke(
                width = if (selected) (3.4f * scale).coerceAtLeast(2.4f)
                else (1.2f * scale).coerceAtLeast(1f)
            ),
        )

        drawRoundRect(
            color = accent,
            topLeft = Offset(rect.left + 5f, rect.top + 9f),
            size = Size(barW, max(rect.height - 18f, 2f)),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )

        if (detail < 2) continue

        nameMetrics[node.customer.id]?.let { m ->
            drawText(
                textLayoutResult = m.text,
                color = pal.textPrimary,
                topLeft = Offset(
                    rect.center.x - m.text.size.width / 2f + barW,
                    rect.center.y - m.text.size.height / 2f,
                ),
            )
        }

        if (!node.customer.firstCardDate.isNullOrBlank()) {
            drawCircle(color = accent, radius = 3f, center = Offset(rect.right - 9f, rect.top + 9f))
        }

        val br = node.badgeRect ?: continue
        drawRoundRect(
            color = accent.copy(alpha = if (node.collapsed) 0.22f else 0.13f),
            topLeft = br.topLeft,
            size = br.size,
            cornerRadius = CornerRadius(br.height / 2f, br.height / 2f),
        )
        badgeMetrics[node.customer.id]?.let { t ->
            drawText(
                textLayoutResult = t,
                color = accent,
                topLeft = Offset(br.left + 9f, br.center.y - t.size.height / 2f),
            )
        }
        val ax = br.right - 11f
        val ay = br.center.y
        val tri = Path()
        if (node.collapsed) {
            tri.moveTo(ax - 2.5f, ay - 4.5f)
            tri.lineTo(ax + 3.5f, ay)
            tri.lineTo(ax - 2.5f, ay + 4.5f)
        } else {
            tri.moveTo(ax - 4.5f, ay - 2.5f)
            tri.lineTo(ax + 4.5f, ay - 2.5f)
            tri.lineTo(ax, ay + 3.5f)
        }
        tri.close()
        drawPath(tri, color = accent)
    }
}

/** 圆角折线：在每个拐点用三次贝塞尔倒角 */
private fun roundedPolyline(points: List<Offset>, radius: Float): Path {
    val path = Path()
    if (points.size < 2) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size - 1) {
        val prev = points[i - 1]
        val cur = points[i]
        val next = points[i + 1]
        val d1 = distance(prev, cur)
        val d2 = distance(cur, next)
        val r = min(radius, min(d1, d2) / 2f)
        if (r <= 0.01f) {
            path.lineTo(cur.x, cur.y)
            continue
        }
        val p1 = Offset(cur.x + (prev.x - cur.x) / d1 * r, cur.y + (prev.y - cur.y) / d1 * r)
        val p2 = Offset(cur.x + (next.x - cur.x) / d2 * r, cur.y + (next.y - cur.y) / d2 * r)
        val c1 = Offset(p1.x + (cur.x - p1.x) * 2f / 3f, p1.y + (cur.y - p1.y) * 2f / 3f)
        val c2 = Offset(p2.x + (cur.x - p2.x) * 2f / 3f, p2.y + (cur.y - p2.y) * 2f / 3f)
        path.lineTo(p1.x, p1.y)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
}

@Composable
private fun EmptyHint(onCreateFirst: () -> Unit, modifier: Modifier = Modifier) {
    val pal = palette
    val d = LocalDimens.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(pal.surface)
            .padding(horizontal = 30.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "客 脉 图",
            style = TextStyle(fontSize = d.pageTitle, fontWeight = FontWeight.SemiBold),
            color = pal.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "客户成脉，往来成图",
            style = TextStyle(fontSize = d.caption),
            color = pal.accent,
        )
        Spacer(Modifier.height(22.dp))
        val onAccent = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
        Text(
            text = "还没有客户。新建一位客户后，\n用它的「加下级」「加同级」把关系铺开。",
            style = TextStyle(fontSize = d.caption),
            color = pal.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(pal.accent)
                .clickable { onCreateFirst() }
                .padding(horizontal = 24.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KemaiIcon(
                id = R.drawable.ic_add,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "新建第一位客户",
                style = TextStyle(fontSize = d.label, fontWeight = FontWeight.Medium),
                color = onAccent,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "之后新增客户，主要靠选中卡片后\n点「加下级」或「加同级」",
            style = TextStyle(fontSize = d.caption),
            color = pal.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
