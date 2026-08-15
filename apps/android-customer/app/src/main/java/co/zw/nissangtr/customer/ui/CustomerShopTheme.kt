package co.zw.nissangtr.customer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.shop.ShopTheme
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Customer-app presentation on top of frozen [ShopTheme]:
 * cool steel / mist / chalk neutrals + GTR red CTAs, with softer field/button corners.
 * Does not edit `packages/android-ui`.
 */
private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Light: chalk ground, white cards, mist chips — no cream/brown. */
private val CoolLight = lightColorScheme(
    primary = GtrColors.Primary,
    onPrimary = GtrColors.PrimaryInk,
    primaryContainer = Color(0xFFF3D6DB),
    onPrimaryContainer = GtrColors.Steel,
    secondary = GtrColors.SteelLift,
    onSecondary = GtrColors.White,
    secondaryContainer = GtrColors.Mist,
    onSecondaryContainer = GtrColors.Steel,
    tertiary = GtrColors.Accent,
    onTertiary = GtrColors.PrimaryInk,
    tertiaryContainer = Color(0xFFD4EDE4),
    onTertiaryContainer = GtrColors.Accent,
    error = GtrColors.Danger,
    onError = GtrColors.PrimaryInk,
    errorContainer = Color(0xFFF3D6DB),
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

/** Dark: steel ground, steel-lift surfaces, silver type — no warm brown. */
private val CoolDark = darkColorScheme(
    primary = GtrColors.PrimaryHover,
    onPrimary = GtrColors.PrimaryInk,
    primaryContainer = Color(0xFF5A1522),
    onPrimaryContainer = Color(0xFFFFDAD9),
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
    surfaceVariant = Color(0xFF2A3140),
    onSurfaceVariant = GtrColors.SilverDim,
    outline = GtrColors.SilverDim,
    outlineVariant = Color(0xFF2A3140),
    inverseSurface = GtrColors.Chalk,
    inverseOnSurface = GtrColors.Steel,
    inversePrimary = GtrColors.Primary,
)

@Composable
fun CustomerShopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    ShopTheme(darkTheme = darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) CoolDark else CoolLight,
            typography = MaterialTheme.typography,
            shapes = SoftShapes,
            content = content,
        )
    }
}
