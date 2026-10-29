package com.aikeybridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aikeybridge.R
import com.aikeybridge.data.ThemeMode

@Immutable
data class BridgePalette(
    val ink: Color,
    val surface: Color,
    val surface2: Color,
    val hairline: Color,
    val track: Color,
    val bar: Color,
    val dotOff: Color,
    val ringBase: Color,
    val navPill: Color,
    val textMain: Color,
    val textSoft: Color,
    val textBody: Color,
    val textIcon: Color,
    val muted: Color,
    val textLabel: Color,
    val textDim: Color,
    val textFaint: Color,
    val textGhost: Color,
    val textHidden: Color,
    val accent: Color,
    val accentSoft: Color,
    val communityBg: Color,
    val communityLine: Color,
    val communityTitle: Color,
    val communitySub: Color,
    val danger: Color,
    val dangerTitle: Color,
    val dangerText: Color,
    val dangerBody: Color,
    val dangerMuted: Color,
    val dangerBg: Color,
    val dangerLine: Color,
    val warn: Color,
)

// Giao diện tối — khớp biến $ink, $surface, $surface-2, $hairline trong untitled.pen.
val DarkPalette = BridgePalette(
    ink = Color(0xFF0A0B0D),
    surface = Color(0xFF14161A),
    surface2 = Color(0xFF0F1115),
    hairline = Color(0xFF23262C),
    track = Color(0xFF23262C),
    bar = Color(0xFF262B32),
    dotOff = Color(0xFF2C3038),
    ringBase = Color(0xFF262A31),
    navPill = Color(0xFF2E333A),
    textMain = Color(0xFFF5F3EE),
    textSoft = Color(0xFFD6D9DE),
    textBody = Color(0xFFC9CCD2),
    textIcon = Color(0xFFA9AEB8),
    muted = Color(0xFF9BA1AC),
    textLabel = Color(0xFF8B929C),
    textDim = Color(0xFF6E7480),
    textFaint = Color(0xFF5B626C),
    textGhost = Color(0xFF4E5560),
    textHidden = Color(0xFF3F4650),
    accent = Color(0xFFCBF25C),
    accentSoft = Color(0xFFEDF7D2),
    communityBg = Color(0xFF14180C),
    communityLine = Color(0xFF2C3518),
    communityTitle = Color(0xFFE8F0CE),
    communitySub = Color(0xFF7E8B55),
    danger = Color(0xFFE4664A),
    dangerTitle = Color(0xFFF0B5A4),
    dangerText = Color(0xFFE9A493),
    dangerBody = Color(0xFFB78A7C),
    dangerMuted = Color(0xFF8A5A4C),
    dangerBg = Color(0xFF1B1210),
    dangerLine = Color(0xFF4A2A21),
    warn = Color(0xFFE0A21B),
)

// Giao diện sáng — khớp tông $paper (#F5F2EA), $paper-2 (#E7E2D5), $ink-text (#12141A) trong untitled.pen.
val LightPalette = BridgePalette(
    ink = Color(0xFFF5F2EA),
    surface = Color(0xFFFCFBF7),
    surface2 = Color(0xFFECE7DA),
    hairline = Color(0xFFD9D3C2),
    track = Color(0xFFE2DDD0),
    bar = Color(0xFFD8D2C3),
    dotOff = Color(0xFFD2CCBC),
    ringBase = Color(0xFFDAD4C4),
    navPill = Color(0xFFC8C2B2),
    textMain = Color(0xFF12141A),
    textSoft = Color(0xFF22262E),
    textBody = Color(0xFF2E333D),
    textIcon = Color(0xFF484E58),
    muted = Color(0xFF525862),
    textLabel = Color(0xFF5C626D),
    textDim = Color(0xFF686E79),
    textFaint = Color(0xFF74796E),
    textGhost = Color(0xFF858374),
    textHidden = Color(0xFFA8A393),
    accent = Color(0xFF3E6800),
    accentSoft = Color(0xFFE3F2B8),
    communityBg = Color(0xFFEEF5DC),
    communityLine = Color(0xFFC8DBA0),
    communityTitle = Color(0xFF233600),
    communitySub = Color(0xFF536B22),
    danger = Color(0xFFC83A22),
    dangerTitle = Color(0xFF8E2210),
    dangerText = Color(0xFFB02E18),
    dangerBody = Color(0xFF6E3529),
    dangerMuted = Color(0xFF8C4A3B),
    dangerBg = Color(0xFFFAECE8),
    dangerLine = Color(0xFFE8C2B8),
    warn = Color(0xFFB86B00),
)

val LocalBridgePalette = staticCompositionLocalOf { DarkPalette }

// Bề mặt — tự động đổi theo giao diện đang chọn.
val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.ink
val Surface: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.surface
val Surface2: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.surface2
val Hairline: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.hairline
val Track: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.track
val Bar: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.bar
val DotOff: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.dotOff
val RingBase: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.ringBase
val NavPill: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.navPill

// Chữ — tự động đổi độ tương phản theo nền sáng/tối.
val TextMain: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textMain
val TextSoft: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textSoft
val TextBody: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textBody
val TextIcon: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textIcon
val Muted: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.muted
val TextLabel: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textLabel
val TextDim: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textDim
val TextFaint: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textFaint
val TextGhost: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textGhost
val TextHidden: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.textHidden

val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.accent
val AccentSoft: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.accentSoft

// Thẻ cộng đồng (màu lime).
val CommunityBg: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.communityBg
val CommunityLine: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.communityLine
val CommunityTitle: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.communityTitle
val CommunitySub: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.communitySub

val Danger: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.danger
val DangerTitle: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.dangerTitle
val DangerText: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.dangerText
val DangerBody: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.dangerBody
val DangerMuted: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.dangerMuted
val DangerBg: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.dangerBg
val DangerLine: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.dangerLine
val Warn: Color
    @Composable @ReadOnlyComposable get() = LocalBridgePalette.current.warn

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

private val DarkBridgeColors = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = DarkPalette.ink,
    primaryContainer = Color(0xFF2B3516),
    onPrimaryContainer = DarkPalette.accentSoft,
    secondary = DarkPalette.textMain,
    onSecondary = DarkPalette.ink,
    background = DarkPalette.ink,
    onBackground = DarkPalette.textMain,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.textMain,
    surfaceVariant = DarkPalette.surface2,
    onSurfaceVariant = DarkPalette.muted,
    surfaceContainerHigh = DarkPalette.surface,
    outline = DarkPalette.hairline,
    outlineVariant = DarkPalette.hairline,
    error = DarkPalette.danger,
    onError = DarkPalette.ink,
    errorContainer = DarkPalette.dangerBg,
    onErrorContainer = DarkPalette.dangerTitle,
)

private val LightBridgeColors = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = LightPalette.ink,
    primaryContainer = LightPalette.accentSoft,
    onPrimaryContainer = LightPalette.communityTitle,
    secondary = LightPalette.textMain,
    onSecondary = LightPalette.ink,
    background = LightPalette.ink,
    onBackground = LightPalette.textMain,
    surface = LightPalette.surface,
    onSurface = LightPalette.textMain,
    surfaceVariant = LightPalette.surface2,
    onSurfaceVariant = LightPalette.muted,
    surfaceContainerHigh = LightPalette.surface,
    outline = LightPalette.hairline,
    outlineVariant = LightPalette.hairline,
    error = LightPalette.danger,
    onError = LightPalette.ink,
    errorContainer = LightPalette.dangerBg,
    onErrorContainer = LightPalette.dangerTitle,
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
fun BridgeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) DarkBridgeColors else LightBridgeColors
    CompositionLocalProvider(LocalBridgePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BridgeTypography,
            content = content,
        )
    }
}
