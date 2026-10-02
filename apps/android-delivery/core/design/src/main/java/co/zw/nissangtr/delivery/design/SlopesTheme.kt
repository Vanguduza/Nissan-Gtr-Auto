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
 * Driver app visual system, modelled on Slopes: a map-first layout with a light grouped
 * background, white inset cards, one blue accent, bold large titles and number-led stats.
 * Light and dark modes share the same structure; only the palette changes.
 */
@Immutable
data class SlopesColors(
    val isDark: Boolean,
    /** Grouped page background behind cards (iOS systemGroupedBackground). */
    val background: Color,
    /** Sheets, cards and list groups. */
    val surface: Color,
    /** Search fields, segmented tracks, inactive tiles. */
    val fill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    /** Tinted accent fill for secondary action tiles and round buttons. */
    val accentTint: Color,
    val onAccent: Color,
    val danger: Color,
    val dangerTint: Color,
    val success: Color,
    val warning: Color,
    /** Route line on the map (Slopes draws tracks in red). */
    val route: Color,
    val mapChrome: Color,
    val scrim: Color,
)

val SlopesLight = SlopesColors(
    isDark = false,
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    fill = Color(0xFFE9E9EE),
    label = Color(0xFF111114),
    secondaryLabel = Color(0xFF6E6E76),
    tertiaryLabel = Color(0xFFA3A3AB),
    separator = Color(0xFFE3E3E8),
    accent = Color(0xFF1F66C9),
    accentTint = Color(0xFFDCE8F8),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFD9262E),
    dangerTint = Color(0xFFFBE3E4),
    success = Color(0xFF2FA84F),
    warning = Color(0xFFE08A00),
    route = Color(0xFFE5262B),
    mapChrome = Color(0xF7FFFFFF),
    scrim = Color(0x33000000),
)

val SlopesDark = SlopesColors(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    fill = Color(0xFF2C2C30),
    label = Color(0xFFF5F5F7),
    secondaryLabel = Color(0xFF9A9AA2),
    tertiaryLabel = Color(0xFF5E5E66),
    separator = Color(0xFF34343A),
    accent = Color(0xFF4A90F2),
    accentTint = Color(0xFF17283F),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFFF4D4F),
    dangerTint = Color(0xFF3A1A1C),
    success = Color(0xFF3CCB62),
    warning = Color(0xFFFFA726),
    route = Color(0xFFFF4D4F),
    mapChrome = Color(0xF21C1C1E),
    scrim = Color(0x66000000),
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
