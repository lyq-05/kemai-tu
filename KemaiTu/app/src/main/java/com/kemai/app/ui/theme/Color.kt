package com.kemai.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- 强调色：青瓷蓝绿 ----------
val QingCiLight = Color(0xFF2F6E6A)
val QingCiDark = Color(0xFF7FC9C2)

// ---------- 画布（中性底，保证子树色相可读） ----------
val CanvasBgLight = Color(0xFFFBFAF7)
val CanvasBgDark = Color(0xFF14181A)
val GridDotLight = Color(0x14000000)
val GridDotDark = Color(0x12FFFFFF)

// ---------- 卡片与文字 ----------
val CardBgLight = Color(0xFFFFFFFF)
val CardBgDark = Color(0xFF1E2426)
val TextPrimaryLight = Color(0xFF1C2321)
val TextPrimaryDark = Color(0xFFE8EDEB)
val TextSecondaryLight = Color(0xFF6B7674)
val TextSecondaryDark = Color(0xFF93A09D)
val DividerLight = Color(0xFFE6E4DF)
val DividerDark = Color(0xFF2C3436)

// ---------- 部件底色 ----------
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF1A1F21)
val SurfaceVariantLight = Color(0xFFF1EFEA)
val SurfaceVariantDark = Color(0xFF262D2F)

// ---------- 子树色相（同一棵子树同一色相） ----------
val SubtreeColorsLight = listOf(
    Color(0xFF2F6E6A), // 1 青瓷
    Color(0xFF3A5FA8), // 2 靛蓝
    Color(0xFF4A7C3F), // 3 松绿
    Color(0xFFA8573A), // 4 赭石
    Color(0xFF6B4F8A), // 5 藤紫
    Color(0xFF2C4A6E), // 6 藏青
)

val SubtreeColorsDark = listOf(
    Color(0xFF7FC9C2),
    Color(0xFF8FAEE8),
    Color(0xFF9BC98F),
    Color(0xFFE0A188),
    Color(0xFFB79ED6),
    Color(0xFF8BA8C9),
)

fun subtreeColor(rootIndex: Int, dark: Boolean): Color {
    val list = if (dark) SubtreeColorsDark else SubtreeColorsLight
    return list[((rootIndex % list.size) + list.size) % list.size]
}

/**
 * 按脉络深度的配色。
 * 第 1 层（根客户）最深，越往下越浅 —— 这样一眼就能看出"谁在哪一层"，
 * 而不是"属于哪棵树"。相邻两层之间色相拉开，保证区分度。
 */
val DepthColorsLight = listOf(
    Color(0xFF1F5F63), // 第1层 深青瓷
    Color(0xFF3A6FA8), // 第2层 靛蓝
    Color(0xFF6B5FA8), // 第3层 紫罗兰
    Color(0xFF9C5A8A), // 第4层 玫紫
    Color(0xFFA87A3A), // 第5层 琥珀
    Color(0xFF5F8E5A), // 第6层 橄榄绿
)

val DepthColorsDark = listOf(
    Color(0xFF8FD4CE),
    Color(0xFF9CBCF0),
    Color(0xFFBFAEE8),
    Color(0xFFE0A5C8),
    Color(0xFFE0B87A),
    Color(0xFFA8D49C),
)

/** 深色模式下的语义色板 */
data class KemaiPalette(
    val dark: Boolean,
    val accent: Color,
    val canvasBg: Color,
    val gridDot: Color,
    val cardBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val surface: Color,
    val surfaceVariant: Color,
) {
    fun subtree(rootIndex: Int): Color = subtreeColor(rootIndex, dark)

    /** 按脉络深度的配色：第 1 层最深，越往下越浅，层级一眼看清 */
    fun depthColor(depth: Int): Color {
        val list = if (dark) DepthColorsDark else DepthColorsLight
        val d = ((depth % list.size) + list.size) % list.size
        return list[d]
    }

    /** 卡片左侧色条 / 描边颜色 */
    fun nodeAccent(depth: Int, rootIndex: Int, byDepth: Boolean): Color =
        if (byDepth) {
            depthColor(depth)
        } else {
            // 家族模式：同子树同色相，但越往下越淡，避免整棵树一个死色
            lerpColor(subtree(rootIndex), cardBg, (depth * 0.16f).coerceAtMost(0.70f))
        }

    /** 卡片填充：底色里掺一点点该节点的颜色，形成层级感但不抢眼 */
    fun nodeFill(depth: Int, rootIndex: Int, byDepth: Boolean): Color {
        val base = if (byDepth) depthColor(depth) else subtree(rootIndex)
        return lerpColor(cardBg, base, if (dark) 0.20f else 0.08f)
    }

    /** 连线颜色：跟着来源节点的颜色走 */
    fun linkColor(depth: Int, rootIndex: Int, byDepth: Boolean): Color =
        nodeAccent(depth, rootIndex, byDepth).copy(alpha = if (dark) 0.88f else 0.80f)

    companion object {
        fun of(dark: Boolean): KemaiPalette = if (dark) {
            KemaiPalette(
                dark = true,
                accent = QingCiDark,
                canvasBg = CanvasBgDark,
                gridDot = GridDotDark,
                cardBg = CardBgDark,
                textPrimary = TextPrimaryDark,
                textSecondary = TextSecondaryDark,
                divider = DividerDark,
                surface = SurfaceDark,
                surfaceVariant = SurfaceVariantDark,
            )
        } else {
            KemaiPalette(
                dark = false,
                accent = QingCiLight,
                canvasBg = CanvasBgLight,
                gridDot = GridDotLight,
                cardBg = CardBgLight,
                textPrimary = TextPrimaryLight,
                textSecondary = TextSecondaryLight,
                divider = DividerLight,
                surface = SurfaceLight,
                surfaceVariant = SurfaceVariantLight,
            )
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt,
    )
}
