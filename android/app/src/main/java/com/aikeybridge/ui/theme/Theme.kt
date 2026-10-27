package com.aikeybridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aikeybridge.R

// Bề mặt — khớp biến $ink, $surface, $surface-2, $hairline trong untitled.pen.
val Ink = Color(0xFF0A0B0D)
val Surface = Color(0xFF14161A)
val Surface2 = Color(0xFF0F1115)
val Hairline = Color(0xFF23262C)
val Track = Color(0xFF23262C)
val Bar = Color(0xFF262B32)
val DotOff = Color(0xFF2C3038)
val RingBase = Color(0xFF262A31)
val NavPill = Color(0xFF2E333A)

// Chữ — từ sáng tới tối.
val TextMain = Color(0xFFF5F3EE)
val TextSoft = Color(0xFFD6D9DE)
val TextBody = Color(0xFFC9CCD2)
val TextIcon = Color(0xFFA9AEB8)
val Muted = Color(0xFF9BA1AC)
val TextLabel = Color(0xFF8B929C)
val TextDim = Color(0xFF6E7480)
val TextFaint = Color(0xFF5B626C)
val TextGhost = Color(0xFF4E5560)
val TextHidden = Color(0xFF3F4650)

val Accent = Color(0xFFCBF25C)
val AccentSoft = Color(0xFFEDF7D2)

// Thẻ cộng đồng (màu lime trầm).
val CommunityBg = Color(0xFF14180C)
val CommunityLine = Color(0xFF2C3518)
val CommunityTitle = Color(0xFFE8F0CE)
val CommunitySub = Color(0xFF7E8B55)

val Danger = Color(0xFFE4664A)
val DangerTitle = Color(0xFFF0B5A4)
val DangerText = Color(0xFFE9A493)
val DangerBody = Color(0xFFB78A7C)
val DangerMuted = Color(0xFF8A5A4C)
val DangerBg = Color(0xFF1B1210)
val DangerLine = Color(0xFF4A2A21)
val Warn = Color(0xFFE0A21B)

val BodyFont = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
val MonoFont = FontFamily(
    Font(
        R.font.jetbrains_mono,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.jetbrains_mono,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

private val BridgeColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2B3516),
    onPrimaryContainer = AccentSoft,
    secondary = TextMain,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextMain,
    surface = Surface,
    onSurface = TextMain,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    surfaceContainerHigh = Surface,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Danger,
    onError = Ink,
    errorContainer = DangerBg,
    onErrorContainer = DangerTitle,
)

private val BridgeTypography = Typography(
    headlineMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = MonoFont, fontSize = 10.sp),
    labelSmall = TextStyle(fontFamily = MonoFont, fontSize = 9.sp),
)

@Composable
fun BridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BridgeColors,
        typography = BridgeTypography,
        content = content,
    )
}
