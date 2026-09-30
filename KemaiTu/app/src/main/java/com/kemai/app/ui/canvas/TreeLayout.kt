package com.kemai.app.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kemai.app.model.AppData
import com.kemai.app.model.Customer
import com.kemai.app.model.LayoutMode

/** 一个已经算好位置的节点 */
data class LayoutNode(
    val customer: Customer,
    /** 卡片矩形（画布坐标，scale=1 时的像素） */
    val rect: Rect,
    val depth: Int,
    val rootIndex: Int,
    /** 直接下级数量 */
    val childCount: Int,
    /** 全部子孙数量（含被折叠起来的） */
    val descendantCount: Int,
    /** 「n 位下级」折叠按钮；没有下级时为 null */
    val badgeRect: Rect?,
    val isRoot: Boolean,
    val collapsed: Boolean,
)

/** 父 → 子 的圆角折线 */
data class LayoutLink(
    val fromId: String,
    val toId: String,
    /** 折线拐点，至少 2 个 */
    val points: List<Offset>,
)

data class LayoutResult(
    val nodes: List<LayoutNode>,
    val links: List<LayoutLink>,
    val bounds: Rect,
) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun nodeById(id: String?): LayoutNode? =
        if (id == null) null else nodes.firstOrNull { it.customer.id == id }

    /** 命中卡片 */
    fun nodeAt(p: Offset): LayoutNode? = nodes.lastOrNull { it.rect.contains(p) }

    /** 命中折叠按钮 */
    fun badgeAt(p: Offset): LayoutNode? = nodes.lastOrNull { it.badgeRect?.contains(p) == true }
}

/**
 * 紧凑树布局（Reingold–Tilford 变体的双方向实现）。
 *
 * 两种脉络共用同一套算法，只是把「主轴 / 交叉轴」映射到不同坐标：
 *
 *  - **竖向脉络**（默认）：主轴 = 纵轴（层级越深越靠下），交叉轴 = 横轴（同级左右并排）
 *  - **横向脉络**：主轴 = 横轴（层级越深越靠右），交叉轴 = 纵轴（同级上下并排）
 *
 * 无论哪种，父节点都会在交叉轴上**居中于其所有子节点**，折叠的子树不占交叉轴空间，
 * 深度与广度都不设上限。
 *
 * 全部尺寸单位都是「画布像素」（scale = 1 时的 px），由调用方用 dp 换算后传入。
 */
class TreeLayoutEngine(
    val mode: LayoutMode,
    val nodeHeight: Float,
    val siblingGap: Float,
    val levelGap: Float,
    val rootGap: Float,
    val badgeGap: Float,
    val badgeHeight: Float,
) {

    fun compute(
        data: AppData,
        widthOf: (Customer) -> Float,
        badgeWidthOf: (Customer) -> Float,
    ): LayoutResult {
        if (data.customers.isEmpty()) {
            return LayoutResult(emptyList(), emptyList(), Rect.Zero)
        }

        val byId = data.customers.associateBy { it.id }
        val ordered = data.customers.sortedBy { it.createdAt }
        val vertical = mode == LayoutMode.VERTICAL

        // ---------- 1. 建立森林，天然免疫数据里的环 ----------
        val visited = HashSet<String>()
        val childrenMap = HashMap<String, List<Customer>>()
        val roots = mutableListOf<Customer>()

        fun visit(c: Customer) {
            visited += c.id
            val kids = ordered.filter { it.referrerId == c.id && it.id != c.id && it.id !in visited }
            childrenMap[c.id] = kids
            for (k in kids) visit(k)
        }

        for (c in ordered) {
            val ref = c.referrerId
            val hasValidRef = ref != null && ref != c.id && byId.containsKey(ref)
            if (!hasValidRef && c.id !in visited) {
                roots += c
                visit(c)
            }
        }
        // 兜底：数据被外部改坏留下成环的孤岛，逐个提升为根，保证「一个都不丢」
        for (c in ordered) {
            if (c.id !in visited) {
                roots += c
                visit(c)
            }
        }

        // ---------- 2. 量尺寸 ----------
        val cardWidth = HashMap<String, Float>()
        val badgeWidth = HashMap<String, Float>()
        for (c in ordered) {
            cardWidth[c.id] = widthOf(c)
            badgeWidth[c.id] = badgeWidthOf(c)
        }

        fun visibleChildren(c: Customer): List<Customer> =
            if (c.collapsed) emptyList() else childrenMap[c.id].orEmpty()

        /**
         * 交叉轴上的占位大小。
         * 竖向时就是卡片宽度；横向时是卡片高度 —— 而且有下级的话还要给下方的
         * 「n 位下级」按钮留出高度，否则相邻兄弟会压在一起。
         */
        fun crossSize(c: Customer): Float =
            if (vertical) cardWidth.getValue(c.id)
            else nodeHeight + if (childrenMap[c.id].orEmpty().isNotEmpty()) badgeGap + badgeHeight else 0f

        val subtreeCrossCache = HashMap<String, Float>()
        fun subtreeCross(c: Customer): Float = subtreeCrossCache.getOrPut(c.id) {
            val own = crossSize(c)
            val kids = visibleChildren(c)
            if (kids.isEmpty()) {
                own
            } else {
                var sum = 0f
                for (k in kids) sum += subtreeCross(k)
                sum += siblingGap * (kids.size - 1)
                maxOf(own, sum)
            }
        }

        // ---------- 3. 主轴（层级）位置 ----------
        val depths = HashMap<String, Int>()
        depths[roots.firstOrNull()?.id ?: ""] = 0
        // 先做一次遍历把每个节点的深度定下来
        val depthStack = ArrayDeque<Pair<Customer, Int>>()
        for (r in roots) depthStack.addLast(r to 0)
        val seenDepth = HashSet<String>()
        while (depthStack.isNotEmpty()) {
            val (c, dep) = depthStack.removeLast()
            if (!seenDepth.add(c.id)) continue
            depths[c.id] = dep
            for (k in childrenMap[c.id].orEmpty()) depthStack.addLast(k to dep + 1)
        }

        val mainOffset = HashMap<Int, Float>()
        if (vertical) {
            // 竖向：每层高度固定，直接按层号推
            var y = 0f
            var d = 0
            while (d <= (depths.values.maxOrNull() ?: 0)) {
                mainOffset[d] = y
                y += nodeHeight + levelGap
                d++
            }
        } else {
            // 横向：每层的宽度取该层最宽的卡片，再累加
            val maxDepth = depths.values.maxOrNull() ?: 0
            val widestAt = HashMap<Int, Float>()
            for (c in ordered) {
                val d = depths[c.id] ?: 0
                widestAt[d] = maxOf(widestAt[d] ?: 0f, cardWidth.getValue(c.id))
            }
            var x = 0f
            for (d in 0..maxDepth) {
                mainOffset[d] = x
                x += (widestAt[d] ?: 0f) + levelGap
            }
        }

        // ---------- 4. 交叉轴摆位 ----------
        val crossPos = HashMap<String, Float>()
        val rootIndexes = HashMap<String, Int>()

        fun place(c: Customer, crossStart: Float, rootIndex: Int) {
            rootIndexes[c.id] = rootIndex
            val cs = crossSize(c)
            val span = subtreeCross(c)
            val kids = visibleChildren(c)

            if (kids.isEmpty()) {
                crossPos[c.id] = crossStart
                return
            }

            var kidsSpan = 0f
            for (k in kids) kidsSpan += subtreeCross(k)
            kidsSpan += siblingGap * (kids.size - 1)

            var cursor = crossStart + (span - kidsSpan) / 2f
            val kidStarts = ArrayList<Pair<Customer, Float>>(kids.size)
            for (k in kids) {
                place(k, cursor, rootIndex)
                kidStarts += k to cursor
                cursor += subtreeCross(k) + siblingGap
            }

            val firstStart = kidStarts.first().second
            val lastKid = kidStarts.last().first
            val lastEnd = kidStarts.last().second + subtreeCross(lastKid)
            val center = (firstStart + lastEnd) / 2f
            crossPos[c.id] = (center - cs / 2f).coerceIn(crossStart, (crossStart + span - cs).coerceAtLeast(crossStart))
        }

        var cursorCross = 0f
        for ((i, r) in roots.withIndex()) {
            place(r, cursorCross, i)
            cursorCross += subtreeCross(r) + rootGap
        }

        // ---------- 5. 归一化 + 组装节点 ----------
        // 注意：这里只处理**真正被摆过位**的节点。
        // 被折叠隐藏的子树不在 crossPos 里，如果按 customers 全量遍历，
        // 它们会拿到 0f 兜底坐标，于是"假装还在图上"照旧被画出来。
        val rawRects = HashMap<String, Rect>()
        for (id in crossPos.keys) {
            val c = byId[id] ?: continue
            val d = depths[id] ?: 0
            val m = mainOffset[d] ?: 0f
            val cr = crossPos.getValue(id)
            val w = cardWidth.getValue(id)
            rawRects[id] = if (vertical) {
                Rect(cr, m, cr + w, m + nodeHeight)
            } else {
                Rect(m, cr, m + w, cr + nodeHeight)
            }
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        for (r in rawRects.values) {
            if (r.left < minX) minX = r.left
            if (r.top < minY) minY = r.top
        }
        val dx = -minX
        val dy = -minY
        fun shift(r: Rect) = r.translate(dx, dy)

        val nodes = ArrayList<LayoutNode>(ordered.size)
        for (c in ordered) {
            val base = rawRects[c.id] ?: continue
            val rect = shift(base)
            val kids = childrenMap[c.id].orEmpty()
            val badge = if (kids.isNotEmpty()) {
                val bw = badgeWidth.getValue(c.id)
                val cx = rect.center.x
                val top = rect.bottom + badgeGap
                Rect(cx - bw / 2f, top, cx + bw / 2f, top + badgeHeight)
            } else {
                null
            }
            nodes += LayoutNode(
                customer = c,
                rect = rect,
                depth = depths[c.id] ?: 0,
                rootIndex = rootIndexes[c.id] ?: 0,
                childCount = kids.size,
                descendantCount = countDescendants(c.id, childrenMap),
                badgeRect = badge,
                isRoot = roots.any { it.id == c.id },
                collapsed = c.collapsed,
            )
        }

        // ---------- 6. 连线 ----------
        val nodeById = nodes.associateBy { it.customer.id }
        val links = ArrayList<LayoutLink>()
        for (n in nodes) {
            if (n.collapsed) continue
            val kids = childrenMap[n.customer.id].orEmpty()
            if (kids.isEmpty()) continue

            for (k in kids) {
                val kn = nodeById[k.id] ?: continue
                val points: List<Offset> = if (vertical) {
                    // 从卡片下方（有角标就从角标下方）垂直下行 → 横向 → 垂直下行
                    val startX = n.rect.center.x
                    val startY = n.badgeRect?.bottom ?: n.rect.bottom
                    val endX = kn.rect.center.x
                    val endY = kn.rect.top
                    if (kotlin.math.abs(endX - startX) < 0.5f) {
                        listOf(Offset(startX, startY), Offset(endX, endY))
                    } else {
                        val midY = startY + (endY - startY) * 0.55f
                        listOf(
                            Offset(startX, startY),
                            Offset(startX, midY),
                            Offset(endX, midY),
                            Offset(endX, endY),
                        )
                    }
                } else {
                    // 横向：从卡片右缘水平出去 → 纵向 → 水平进入子节点左缘
                    val startX = n.rect.right
                    val startY = n.rect.center.y
                    val endX = kn.rect.left
                    val endY = kn.rect.center.y
                    if (kotlin.math.abs(endY - startY) < 0.5f) {
                        listOf(Offset(startX, startY), Offset(endX, endY))
                    } else {
                        val midX = startX + (endX - startX) * 0.55f
                        listOf(
                            Offset(startX, startY),
                            Offset(midX, startY),
                            Offset(midX, endY),
                            Offset(endX, endY),
                        )
                    }
                }
                links += LayoutLink(n.customer.id, k.id, points)
            }
        }

        // ---------- 7. 包围盒 ----------
        var bMaxX = -Float.MAX_VALUE
        var bMaxY = -Float.MAX_VALUE
        for (n in nodes) {
            bMaxX = maxOf(bMaxX, n.rect.right)
            bMaxY = maxOf(bMaxY, n.rect.bottom)
            n.badgeRect?.let {
                bMaxX = maxOf(bMaxX, it.right)
                bMaxY = maxOf(bMaxY, it.bottom)
            }
        }
        val bounds = Rect(0f, 0f, maxOf(bMaxX, 0f), maxOf(bMaxY, 0f))

        return LayoutResult(nodes, links, bounds)
    }

    private fun countDescendants(id: String, childrenMap: Map<String, List<Customer>>): Int {
        var total = 0
        val stack = ArrayDeque<String>()
        stack.addLast(id)
        val seen = HashSet<String>()
        seen += id
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            for (k in childrenMap[cur].orEmpty()) {
                if (seen.add(k.id)) {
                    total++
                    stack.addLast(k.id)
                }
            }
        }
        return total
    }
}
