package co.zw.nissangtr.delivery.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Driver app visual system: the Slopes layout (map-first, draggable sheet, large titles,
 * number-led stats) in Nissan GTR Auto brand colours (packages/ui/brand-tokens.json: GTR red,
 * steel, silver, chalk) with the owner-approved soft-UI depth (neumorph tokens, D-015).
 * Cards share the canvas colour and are lifted by a light and a dark shadow; tracks and fields
 * are pressed in. Light and dark modes share the same structure; only the palette changes.
 */
@Immutable
data class SlopesColors(
    val isDark: Boolean,
    /** Canvas behind everything; neumorphic cards use the same colour. */
    val background: Color,
    /** Cards, sheets and list groups (same tone as the canvas for soft-UI). */
    val surface: Color,
    /** Pressed-in fill for fields, tracks and inactive controls. */
    val fill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    /** GTR red. */
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val danger: Color,
    val dangerTint: Color,
    val success: Color,
    val warning: Color,
    /** Route line on the map. */
    val route: Color,
    val mapChrome: Color,
    val scrim: Color,
    /** Soft-UI light source (top-left) and shade (bottom-right). */
    val neuHighlight: Color,
    val neuShade: Color,
)

val SlopesLight = SlopesColors(
    isDark = false,
    background = Color(0xFFECEFF3),
    surface = Color(0xFFECEFF3),
    fill = Color(0xFFE0E4EA),
    label = Color(0xFF12151C),
    secondaryLabel = Color(0xFF5E6573),
    tertiaryLabel = Color(0xFF8B929E),
    separator = Color(0xFFD9DEE5),
    accent = Color(0xFFC8102E),
    accentTint = Color(0xFFF6DDE1),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFF8E0F22),
    dangerTint = Color(0xFFF1DADE),
    success = Color(0xFF0B6E4F),
    warning = Color(0xFFB45309),
    route = Color(0xFFC8102E),
    mapChrome = Color(0xF5ECEFF3),
    scrim = Color(0x5212151C),
    neuHighlight = Color(0xFFFFFFFF),
    neuShade = Color(0xFFC9CFD9),
)

val SlopesDark = SlopesColors(
    isDark = true,
    background = Color(0xFF181C24),
    surface = Color(0xFF181C24),
    fill = Color(0xFF12151C),
    label = Color(0xFFF4F5F7),
    secondaryLabel = Color(0xFFA3AAB6),
    tertiaryLabel = Color(0xFF6B7280),
    separator = Color(0xFF2A3241),
    accent = Color(0xFFE01234),
    accentTint = Color(0xFF3A1720),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFEF4444),
    dangerTint = Color(0xFF3B1A1E),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    route = Color(0xFFE01234),
    mapChrome = Color(0xF2181C24),
    scrim = Color(0x99000000),
    neuHighlight = Color(0xFF283040),
    neuShade = Color(0xFF0A0D12),
)

val LocalSlopesColors = staticCompositionLocalOf { SlopesLight }

/** Accessor: `Slopes.colors.accent` inside a [SlopesTheme]. */
object Slopes {
    val colors: SlopesColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSlopesColors.current
}

/** Driver-selectable appearance; System follows the phone. */
enum class SlopesMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

private val Sans = FontFamily.Default

/** Heavy large titles, number-first stats, quiet secondary text — the Slopes rhythm. */
val SlopesTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

val SlopesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SlopesTheme(
    mode: SlopesMode = SlopesMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        SlopesMode.System -> isSystemInDarkTheme()
        SlopesMode.Light -> false
        SlopesMode.Dark -> true
    }
    SlopesTheme(darkTheme = dark, content = content)
}

@Composable
fun SlopesTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) SlopesDark else SlopesLight
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentTint, onPrimaryContainer = c.accent,
            secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.label,
            surface = c.surface, onSurface = c.label, surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerLow = c.surface,
            outline = c.separator, outlineVariant = c.separator,
            error = c.danger, onError = Color.White, errorContainer = c.dangerTint,
            scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentTint, onPrimaryContainer = c.accent,
            secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.label,
            surface = c.surface, onSurface = c.label, surfaceVariant = c.fill, onSurfaceVariant = c.secondaryLabel,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerLow = c.surface,
            outline = c.separator, outlineVariant = c.separator,
            error = c.danger, onError = Color.White, errorContainer = c.dangerTint,
            scrim = c.scrim,
        )
    }
    CompositionLocalProvider(LocalSlopesColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SlopesTypography,
            shapes = SlopesShapes,
            content = content,
        )
    }
}
