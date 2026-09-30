package com.kemai.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 全局字号档位。图标尺寸不随之改变，只缩放文字。
 *
 * 档位对应关系（需求文档 8.3）：
 *  客户名称 16sp / 面板标题 20sp / 正文 14sp
 */
data class KemaiDimens(val scale: Float = 1f) {
    private fun s(base: Int): TextUnit = (base * scale).sp

    /** 导图节点上的客户名称 */
    val nodeTitle: TextUnit get() = s(16)

    /** 面板 / 页面标题 */
    val pageTitle: TextUnit get() = s(20)

    /** 分组小标题 */
    val sectionTitle: TextUnit get() = s(13)

    /** 正文、输入框 */
    val body: TextUnit get() = s(14)

    /** 标签、按钮 */
    val label: TextUnit get() = s(14)

    /** 辅助小字（n 位下级、说明文字） */
    val caption: TextUnit get() = s(12)

    /** 极小字（角标） */
    val tiny: TextUnit get() = s(10)
}

val LocalDimens = staticCompositionLocalOf { KemaiDimens(1f) }
