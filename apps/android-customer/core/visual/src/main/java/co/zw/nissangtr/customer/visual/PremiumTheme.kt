package co.zw.nissangtr.customer.visual

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PremiumDarkScheme = darkColorScheme(
    primary = GtrPremiumColors.Red,
    onPrimary = Color.White,
    primaryContainer = GtrPremiumColors.RedDark,
    onPrimaryContainer = Color.White,

    secondary = GtrPremiumColors.TextSecondary,
    onSecondary = GtrPremiumColors.Background,

    background = GtrPremiumColors.Background,
    onBackground = GtrPremiumColors.TextPrimary,

    surface = GtrPremiumColors.Surface,
    onSurface = GtrPremiumColors.TextPrimary,
    surfaceVariant = GtrPremiumColors.SurfaceRaised,
    onSurfaceVariant = GtrPremiumColors.TextSecondary,

    outline = GtrPremiumColors.Border,
    outlineVariant = GtrPremiumColors.SurfaceSoft,

    error = GtrPremiumColors.RedBright,
    onError = Color.White,
)

private val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun PremiumCustomerTheme(content: @Composable () -> Unit) {
    // Typography is intentionally inherited from ShopTheme/GTR shared typography:
    // Titillium Web display + Source Sans 3 body.
    MaterialTheme(
        colorScheme = PremiumDarkScheme,
        typography = MaterialTheme.typography,
        shapes = PremiumShapes,
        content = content,
    )
}
