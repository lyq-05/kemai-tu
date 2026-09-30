package com.kemai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.kemai.app.model.AppLanguage
import com.kemai.app.model.ColorMode
import com.kemai.app.model.FieldLabels
import com.kemai.app.model.FontScale
import com.kemai.app.model.LayoutMode
import com.kemai.app.model.ThemeMode

val LocalPalette = staticCompositionLocalOf { KemaiPalette.of(false) }

/** 三个内置字段当前叫什么名字（用户可改） */
val LocalFieldLabels = staticCompositionLocalOf { FieldLabels() }

/** 当前脉络方向 */
val LocalLayoutMode = staticCompositionLocalOf { LayoutMode.VERTICAL }

/** 当前配色方式 */
val LocalColorMode = staticCompositionLocalOf { ColorMode.DEPTH }

/** 当前界面语言 */
val LocalLanguage = staticCompositionLocalOf { AppLanguage.ZH }

/** 当前调色板 */
val palette: KemaiPalette
    @Composable get() = LocalPalette.current

/** 当前字段名称 */
val fieldLabels: FieldLabels
    @Composable get() = LocalFieldLabels.current

/** 当前脉络方向 */
val layoutMode: LayoutMode
    @Composable get() = LocalLayoutMode.current

/** 当前配色方式 */
val colorMode: ColorMode
    @Composable get() = LocalColorMode.current

@Composable
fun KemaiTheme(
    themeMode: ThemeMode,
    fontScale: FontScale,
    labels: FieldLabels = FieldLabels(),
    mode: LayoutMode = LayoutMode.VERTICAL,
    colors: ColorMode = ColorMode.DEPTH,
    language: AppLanguage = AppLanguage.ZH,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val pal = remember(dark) { KemaiPalette.of(dark) }
    val dimens = remember(fontScale) { KemaiDimens(fontScale.factor) }

    // 让 Compose 内部（尤其是日期选择器）也跟着应用语言走。
    // 注意：**不能**覆盖 LocalContext —— 那会破坏 rememberLauncherForActivityResult
    // 对 Activity 的查找（备份导出/导入、通知权限申请都靠它）。
    // 所以这里只覆盖 LocalConfiguration，框架层面的语言由 MainActivity 的
    // Locale.setDefault + updateConfiguration 负责。
    val baseConfig = LocalConfiguration.current
    val locale = remember(language) { java.util.Locale.forLanguageTag(language.tag) }
    val config = remember(baseConfig, locale) {
        android.content.res.Configuration(baseConfig).apply { setLocale(locale) }
    }

    val scheme = remember(dark) {
        if (dark) {
            darkColorScheme(
                primary = QingCiDark,
                onPrimary = androidx.compose.ui.graphics.Color(0xFF00312E),
                primaryContainer = androidx.compose.ui.graphics.Color(0xFF1F4E4A),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFA8E6DF),
                secondary = QingCiDark,
                background = CanvasBgDark,
                onBackground = TextPrimaryDark,
                surface = SurfaceDark,
                onSurface = TextPrimaryDark,
                surfaceVariant = SurfaceVariantDark,
                onSurfaceVariant = TextSecondaryDark,
                outline = DividerDark,
                error = androidx.compose.ui.graphics.Color(0xFFE88B8B),
            )
        } else {
            lightColorScheme(
                primary = QingCiLight,
                onPrimary = androidx.compose.ui.graphics.Color.White,
                primaryContainer = androidx.compose.ui.graphics.Color(0xFFCBE8E4),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF10312E),
                secondary = QingCiLight,
                background = CanvasBgLight,
                onBackground = TextPrimaryLight,
                surface = SurfaceLight,
                onSurface = TextPrimaryLight,
                surfaceVariant = SurfaceVariantLight,
                onSurfaceVariant = TextSecondaryLight,
                outline = DividerLight,
                error = androidx.compose.ui.graphics.Color(0xFFB3261E),
            )
        }
    }

    CompositionLocalProvider(
        LocalPalette provides pal,
        LocalDimens provides dimens,
        LocalFieldLabels provides labels,
        LocalLayoutMode provides mode,
        LocalColorMode provides colors,
        LocalLanguage provides language,
        LocalConfiguration provides config,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
