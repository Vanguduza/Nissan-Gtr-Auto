package co.zw.nissangtr.customer.visual

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import androidx.compose.ui.unit.dp

private fun schemeFor(p: GtrPalette): ColorScheme = if (p.isDark) {
    darkColorScheme(
        primary = p.red, onPrimary = Color.White, primaryContainer = p.redDark, onPrimaryContainer = Color.White,
        secondary = p.textSecondary, onSecondary = p.background,
        background = p.background, onBackground = p.textPrimary,
        surface = p.surface, onSurface = p.textPrimary,
        surfaceVariant = p.surfaceRaised, onSurfaceVariant = p.textSecondary,
        outline = p.border, outlineVariant = p.surfaceSoft,
        error = p.redBright, onError = Color.White,
    )
} else {
    lightColorScheme(
        primary = p.red, onPrimary = Color.White, primaryContainer = p.paperWarm, onPrimaryContainer = p.redDark,
        secondary = p.textSecondary, onSecondary = Color.White,
        background = p.background, onBackground = p.textPrimary,
        surface = p.surface, onSurface = p.textPrimary,
        surfaceVariant = p.surfaceSoft, onSurfaceVariant = p.textSecondary,
        outline = p.border, outlineVariant = p.surfaceHigh,
        error = p.red, onError = Color.White,
    )
}

private val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

fun paletteFor(style: CustomerStyle, darkTheme: Boolean): GtrPalette = when (style) {
    CustomerStyle.Illustrated -> if (darkTheme) IllustratedPalette else IllustratedLightPalette
    CustomerStyle.Pos -> if (darkTheme) PosDarkPalette else PosPalette
}

/**
 * Customer theme. [style] picks the illustrated design or the POS look, [darkTheme] its light or
 * dark mode; all four share the same screens, components and typography (inherited from
 * ShopTheme: Titillium Web display + Source Sans 3 body).
 */
@Composable
fun PremiumCustomerTheme(
    style: CustomerStyle = CustomerStyle.Illustrated,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(style, darkTheme)
    val inherited = MaterialTheme.typography
    // POS style: the counter's clean system typeface instead of the illustrated display faces.
    val typography = if (style == CustomerStyle.Pos) inherited.withFamily(FontFamily.Default) else inherited
    CompositionLocalProvider(LocalGtrPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = typography,
            shapes = PremiumShapes,
        ) {
            if (style == CustomerStyle.Pos) {
                // The POS design system (soft depth, palette, motion) for the POS components.
                PosTheme(windowClass = PosWindowClass.CompactPortrait, darkTheme = darkTheme, content = content)
            } else {
                content()
            }
        }
    }
}

private fun Typography.withFamily(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)
