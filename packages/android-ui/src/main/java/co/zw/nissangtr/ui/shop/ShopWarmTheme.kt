package co.zw.nissangtr.ui.shop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Shared warm storefront surface treatment used by customer-facing shopping and the
 * operator POS kiosk. Keeps the same Nissan GTR Auto brand tokens while softening
 * surfaces/corners for high-touch commerce screens.
 */
private val WarmDarkBackground = Color(0xFF1C1714)
private val WarmDarkSurface = Color(0xFF2A221C)
private val WarmDarkVariant = Color(0xFF352C24)
private val WarmOnDark = Color(0xFFF3EDE6)
private val WarmMuted = Color(0xFFC4B5A5)

private val WarmSoftShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val WarmLightScheme = lightColorScheme(
    primary = GtrColors.Primary,
    onPrimary = GtrColors.PrimaryInk,
    primaryContainer = Color(0xFFF5E6E8),
    onPrimaryContainer = GtrColors.Steel,
    secondary = Color(0xFF5C4A3A),
    onSecondary = Color(0xFFFFF8F2),
    background = Color(0xFFF7F1EA),
    onBackground = Color(0xFF1C1714),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF1C1714),
    surfaceVariant = Color(0xFFEDE4DA),
    onSurfaceVariant = Color(0xFF6B5B4D),
    outline = Color(0xFFD4C4B4),
    outlineVariant = Color(0xFFE8DDD2),
    error = GtrColors.Danger,
    onError = GtrColors.PrimaryInk,
)

private val WarmDarkScheme = darkColorScheme(
    primary = GtrColors.PrimaryHover,
    onPrimary = GtrColors.PrimaryInk,
    primaryContainer = WarmDarkVariant,
    onPrimaryContainer = WarmOnDark,
    secondary = WarmMuted,
    onSecondary = WarmDarkBackground,
    background = WarmDarkBackground,
    onBackground = WarmOnDark,
    surface = WarmDarkSurface,
    onSurface = WarmOnDark,
    surfaceVariant = WarmDarkVariant,
    onSurfaceVariant = WarmMuted,
    outline = Color(0xFF6B5A4A),
    outlineVariant = WarmDarkVariant,
    error = GtrColors.PrimaryHover,
    onError = GtrColors.PrimaryInk,
)

@Composable
fun ShopWarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    ShopTheme(darkTheme = darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) WarmDarkScheme else WarmLightScheme,
            typography = MaterialTheme.typography,
            shapes = WarmSoftShapes,
            content = content,
        )
    }
}
