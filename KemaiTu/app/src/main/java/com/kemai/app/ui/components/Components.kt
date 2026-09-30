package com.kemai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kemai.app.ui.theme.LocalDimens
import com.kemai.app.ui.theme.palette

/** 客脉图自绘的细线图标：比 Material 图标更轻，跟整体简约风格统一 */
@Composable
fun KemaiIcon(
    @androidx.annotation.DrawableRes id: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = androidx.compose.ui.res.painterResource(id),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

/** 分组小标题 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val pal = palette
    val d = LocalDimens.current
    Text(
        text = text,
        style = TextStyle(fontSize = d.sectionTitle, fontWeight = FontWeight.SemiBold),
        color = pal.accent,
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

/** 输入框外观统一在这里 */
@Composable
fun KemaiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Int = 44,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val pal = palette
    val d = LocalDimens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(pal.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.merge(
                    TextStyle(fontSize = d.body, color = pal.textPrimary)
                ),
                cursorBrush = SolidColor(pal.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (singleLine) 22.dp else minHeight.dp),
            )
        }
        trailing?.invoke()
    }
}

/** 可点选的标签（产品、字号档位等都用它） */
@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    val d = LocalDimens.current
    val bg = if (selected) pal.accent.copy(alpha = 0.16f) else pal.surfaceVariant
    val fg = if (selected) pal.accent else pal.textSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                width = if (selected) 1.2.dp else 0.dp,
                color = if (selected) pal.accent.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(50),
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = d.label, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = fg,
        )
    }
}

/** 设置项：左标题 + 右值 + 箭头 */
@Composable
fun SettingRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val pal = palette
    val d = LocalDimens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(fontSize = d.body),
                color = pal.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (value != null) {
                Text(
                    text = value,
                    style = TextStyle(fontSize = d.body),
                    color = pal.textSecondary,
                )
                Spacer(Modifier.width(6.dp))
            }
            trailing?.invoke()
            if (onClick != null && trailing == null) {
                KemaiIcon(
                    id = com.kemai.app.R.drawable.ic_chevron_right,
                    contentDescription = null,
                    tint = pal.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(pal.divider.copy(alpha = 0.6f))
            )
        }
    }
}

/** 需要强调的操作行（如「导出备份」） */
@Composable
fun ActionRow(
    title: String,
    icon: ImageVector? = null,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val pal = palette
    val d = LocalDimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: pal.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = TextStyle(fontSize = d.body),
            color = tint ?: pal.textPrimary,
        )
    }
}

/** 圆形主色按钮（右下角悬浮） */
@Composable
fun RoundIconButton(
    @androidx.annotation.DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Int = 52,
    filled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pal = palette
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (filled) pal.accent else pal.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        KemaiIcon(
            id = icon,
            contentDescription = contentDescription,
            tint = if (filled) onAccent else pal.textPrimary,
            modifier = Modifier.size((size * 0.46f).dp),
        )
    }
}
