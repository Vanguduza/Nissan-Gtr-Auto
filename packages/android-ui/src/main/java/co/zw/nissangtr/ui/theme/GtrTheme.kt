package co.zw.nissangtr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GtrDensity {
    /** Customer / staff shop chrome. */
    Standard,
    /** Driver UI — tighter vertical rhythm. */
    Compact,
}

data class GtrThemeExtras(
    val density: GtrDensity = GtrDensity.Standard,
) {
    val screenPadding: Dp
        get() = if (density == GtrDensity.Compact) 12.dp else 16.dp
    val sectionGap: Dp
        get() = if (density == GtrDensity.Compact) 8.dp else 12.dp
    val homePadding: Dp
        get() = if (density == GtrDensity.Compact) 16.dp else 24.dp
}

val LocalGtrExtras = staticCompositionLocalOf { GtrThemeExtras() }

private val LightColors = lightColorScheme(
    primary = GtrColors.Primary,
    onPrimary = GtrColors.PrimaryInk,
    primaryContainer = GtrColors.Mist,
    onPrimaryContainer = GtrColors.Steel,
    secondary = GtrColors.SteelLift,
    onSecondary = GtrColors.Silver,
    secondaryContainer = GtrColors.Mist,
    onSecondaryContainer = GtrColors.Steel,
    tertiary = GtrColors.Accent,
    onTertiary = GtrColors.PrimaryInk,
    tertiaryContainer = GtrColors.Mist,
    onTertiaryContainer = GtrColors.Accent,
    error = GtrColors.Danger,
    onError = GtrColors.PrimaryInk,
    errorContainer = GtrColors.Mist,
    onErrorContainer = GtrColors.Danger,
    background = GtrColors.Chalk,
    onBackground = GtrColors.Steel,
    surface = GtrColors.White,
    onSurface = GtrColors.Steel,
    surfaceVariant = GtrColors.Mist,
    onSurfaceVariant = GtrColors.SilverDim,
    outline = GtrColors.Silver,
    outlineVariant = GtrColors.Mist,
    inverseSurface = GtrColors.Steel,
    inverseOnSurface = GtrColors.Silver,
    inversePrimary = GtrColors.PrimaryHover,
)

/** Staff/driver night-shift chrome — steel ground, red CTA retained. */
private val DarkColors = darkColorScheme(
    primary = GtrColors.Primary,
    onPrimary = GtrColors.PrimaryInk,
    primaryContainer = GtrColors.SteelLift,
    onPrimaryContainer = GtrColors.Silver,
    secondary = GtrColors.Silver,
    onSecondary = GtrColors.Steel,
    secondaryContainer = GtrColors.SteelLift,
    onSecondaryContainer = GtrColors.Silver,
    tertiary = GtrColors.Accent,
    onTertiary = GtrColors.PrimaryInk,
    error = GtrColors.PrimaryHover,
    onError = GtrColors.PrimaryInk,
    background = GtrColors.Steel,
    onBackground = GtrColors.Silver,
    surface = GtrColors.SteelLift,
    onSurface = GtrColors.Silver,
    surfaceVariant = GtrColors.SteelLift,
    onSurfaceVariant = GtrColors.SilverDim,
    outline = GtrColors.SilverDim,
    outlineVariant = GtrColors.SteelLift,
    inverseSurface = GtrColors.Chalk,
    inverseOnSurface = GtrColors.Steel,
    inversePrimary = GtrColors.Primary,
)

/**
 * Brand Material3 theme. Prefer this over bare [MaterialTheme] so all surfaces
 * inherit GTR red/steel/chalk instead of the default purple seed.
 */
@Composable
fun GtrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    density: GtrDensity = GtrDensity.Standard,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGtrExtras provides GtrThemeExtras(density)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = GtrTypography,
            shapes = GtrShapes,
            content = content,
        )
    }
}
