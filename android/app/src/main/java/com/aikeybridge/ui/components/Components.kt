package com.aikeybridge.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.ui.theme.Accent
import com.aikeybridge.ui.theme.BodyFont
import com.aikeybridge.ui.theme.Hairline
import com.aikeybridge.ui.theme.Ink
import com.aikeybridge.ui.theme.MonoFont
import com.aikeybridge.ui.theme.Surface
import com.aikeybridge.ui.theme.Surface2
import com.aikeybridge.ui.theme.TextBody
import com.aikeybridge.ui.theme.TextDim
import com.aikeybridge.ui.theme.TextFaint
import com.aikeybridge.ui.theme.TextGhost
import com.aikeybridge.ui.theme.TextHidden
import com.aikeybridge.ui.theme.TextIcon
import com.aikeybridge.ui.theme.TextLabel
import com.aikeybridge.ui.theme.TextMain
import com.aikeybridge.ui.theme.Track

// ---------- Chữ & icon ----------

@Composable
fun BodyText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = BodyFont,
        fontWeight = weight,
        fontSize = size,
        letterSpacing = letterSpacing,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

@Composable
fun MonoText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = 0.sp,
    maxLines: Int = 1,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = MonoFont,
        fontWeight = weight,
        fontSize = size,
        letterSpacing = letterSpacing,
        lineHeight = size * 1.35f,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun LucideIcon(
    @DrawableRes icon: Int,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = TextGhost) {
    MonoText(text.uppercase(), 9.5.sp, color, modifier, letterSpacing = 1.4.sp)
}

// ---------- Khung ----------

/** Viền dưới 1dp kiểu "hairline" của thiết kế, dùng cho hàng trong panel. */
@Composable
fun Modifier.hairlineBottom(show: Boolean = true, color: Color = Hairline): Modifier =
    if (!show) this else drawBehind {
        val y = size.height - 0.5.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(text, Modifier.weight(1f))
        if (action != null) {
            BodyText(
                action,
                11.sp,
                TextLabel,
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = onAction != null) { onAction?.invoke() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun IconTile(@DrawableRes icon: Int, tint: Color = TextIcon) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(icon, tint, 15.dp)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

// ---------- Điều khiển ----------

@Composable
fun BridgeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val track by animateColorAsState(if (checked && enabled) Accent else Track, label = "track")
    val knob by animateColorAsState(if (checked && enabled) Ink else TextFaint, label = "knob")
    val offset by animateDpAsState(if (checked) 17.dp else 0.dp, label = "offset")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 23.dp)
                .clip(CircleShape)
                .background(track)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset(x = offset)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(knob),
            )
        }
    }
}

@Composable
fun CircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextIcon,
    iconSize: Dp = 15.dp,
) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(34.dp)
            .clip(CircleShape)
            .border(1.dp, Hairline, CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(icon, tint, iconSize, contentDescription = contentDescription)
    }
}

@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 54.dp,
    radius: Dp = 16.dp,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) Accent else Accent.copy(alpha = 0.28f))
            .clickable(enabled = enabled && !loading, onClick = onClick, role = Role.Button)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(15.dp), color = Ink, strokeWidth = 2.dp)
        } else if (leadingIcon != null) {
            LucideIcon(leadingIcon, Ink, 15.dp)
        }
        BodyText(text, 15.sp, Ink.copy(alpha = if (enabled) 1f else 0.6f), weight = FontWeight.SemiBold, maxLines = 1)
        if (trailingIcon != null) {
            LucideIcon(trailingIcon, Ink, 16.dp)
        }
    }
}

@Composable
fun OutlineCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color = TextLabel,
    textColor: Color = TextBody,
    enabled: Boolean = true,
    height: Dp = 46.dp,
    radius: Dp = 14.dp,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .height(height)
            .clip(shape)
            .border(1.dp, Hairline, shape)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LucideIcon(icon, if (enabled) iconTint else TextGhost, 14.dp)
        BodyText(text, 13.5.sp, if (enabled) textColor else TextGhost, maxLines = 1)
    }
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    textColor: Color = TextLabel,
    background: Color = Surface,
    border: Color = Hairline,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) StatusDot(dot, 5.dp)
        MonoText(text, 8.5.sp, textColor)
    }
}

@Composable
fun TinyTag(text: String, color: Color = TextDim, modifier: Modifier = Modifier) {
    MonoText(
        text.uppercase(),
        7.5.sp,
        color,
        modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        letterSpacing = 0.6.sp,
    )
}

@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    label: String,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Hairline, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(R.drawable.lc_minus, "Giảm $label", minusEnabled, onMinus)
        MonoText(value, 10.5.sp, TextMain, Modifier.padding(horizontal = 2.dp))
        StepperButton(R.drawable.lc_plus, "Tăng $label", plusEnabled, onPlus)
    }
}

@Composable
private fun StepperButton(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        LucideIcon(icon, if (enabled) TextIcon else TextHidden, 11.dp, contentDescription = description)
    }
}

data class SegmentOption(val label: String)

@Composable
fun Segmented(
    options: List<SegmentOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) Track else Color.Transparent)
                    .clickable(enabled = !active, role = Role.Tab) { onSelect(index) },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BodyText(option.label, 12.sp, if (active) TextMain else TextDim, maxLines = 1)
            }
        }
    }
}

// ---------- Hàng trong panel ----------

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    titleColor: Color = TextMain,
    titleSize: TextUnit = 14.sp,
    titleWeight: FontWeight = FontWeight.Medium,
    subColor: Color = TextFaint,
    horizontalPadding: Dp = 0.dp,
) {
    Row(
        modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .padding(horizontal = horizontalPadding, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LucideIcon(icon, if (checked && enabled) TextBody else TextFaint, 16.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BodyText(title, titleSize, if (enabled) titleColor else TextLabel, weight = titleWeight, maxLines = 1)
            MonoText(subtitle, 9.sp, subColor)
        }
        BridgeSwitch(checked, onCheckedChange, enabled = enabled)
    }
}

@Composable
fun NavRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
    trailing: String? = null,
    titleColor: Color = TextMain,
    subColor: Color = TextFaint,
    trailingColor: Color = TextDim,
    chevronColor: Color = TextHidden,
    loading: Boolean = false,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .hairlineBottom(divider)
            .clickable(enabled = onClick != null && !loading, role = Role.Button) { onClick?.invoke() }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BodyText(title, 13.5.sp, titleColor, maxLines = 1)
            MonoText(subtitle, 9.sp, subColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(12.dp), color = TextDim, strokeWidth = 1.5.dp)
            } else if (trailing != null) {
                MonoText(trailing, 9.5.sp, trailingColor)
            }
            if (onClick != null) LucideIcon(R.drawable.lc_chevron_right, chevronColor, 13.dp)
        }
    }
}

// ---------- Header & điều hướng ----------

@Composable
fun TabHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 20.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MonoText(eyebrow.uppercase(), 9.sp, TextDim, letterSpacing = 1.8.sp)
            BodyText(title, 24.sp, TextMain, weight = FontWeight.Bold, letterSpacing = (-0.8).sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

@Composable
fun SubHeader(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 24.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleButton(R.drawable.lc_chevron_left, "Quay lại", onBack, tint = TextMain, iconSize = 16.dp)
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun ScreenTitle(text: String, size: TextUnit = 30.sp, modifier: Modifier = Modifier) {
    BodyText(
        text,
        size,
        TextMain,
        modifier,
        weight = FontWeight.Bold,
        letterSpacing = (-1).sp,
        lineHeight = size * 1.15f,
    )
}

data class NavTab(val label: String, @DrawableRes val icon: Int)

val BridgeTabs = listOf(
    NavTab("Tổng quan", R.drawable.lc_layout_dashboard),
    NavTab("Chế độ", R.drawable.lc_sliders_horizontal),
    NavTab("Nhật ký", R.drawable.lc_list_tree),
    NavTab("Cài đặt", R.drawable.lc_settings),
)

@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hairline = Hairline
    Row(
        modifier
            .fillMaxWidth()
            .background(Ink)
            .drawBehind {
                drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 6.dp),
    ) {
        BridgeTabs.forEachIndexed { index, tab ->
            val active = index == selected
            val color = if (active) Accent else TextDim
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LucideIcon(tab.icon, color, 19.dp)
                BodyText(tab.label, 9.sp, color, maxLines = 1)
            }
        }
    }
}

val ScreenPadding = PaddingValues(horizontal = 24.dp)

@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp) = Spacer(Modifier.width(width))
