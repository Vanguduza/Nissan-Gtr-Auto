package co.zw.nissangtr.customer.visual

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The two customer app styles, each with a light and a dark mode. [Illustrated] dark is the locked premium design. */
enum class CustomerStyle { Illustrated, Pos }

/** Every colour the customer screens use. One instance per [CustomerStyle]. */
@Immutable
data class GtrPalette(
    val style: CustomerStyle,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSoft: Color,
    val surfaceHigh: Color,
    val border: Color,
    val red: Color,
    val redBright: Color,
    val redDark: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val paperWarm: Color,
    val paperCool: Color,
    /** Text and link colour on the paper-coloured help / delivery tiles. */
    val paperInk: Color,
    val paperAction: Color,
    /** Hero banners stay dark in both styles (GT-R artwork on black, as in the POS). */
    val heroBackground: Color,
    val heroText: Color,
    val heroTextSecondary: Color,
    /** Outline of product cards: quiet in Illustrated, the POS red lining in POS. */
    val productCardBorder: Color,
    val productCardBorderWidth: Float,
)

val IllustratedPalette = GtrPalette(
    style = CustomerStyle.Illustrated,
    isDark = true,
    background = Color(0xFF090B0F),
    surface = Color(0xFF12151C),
    surfaceRaised = Color(0xFF181C24),
    surfaceSoft = Color(0xFF20242C),
    surfaceHigh = Color(0xFF242A32),
    border = Color(0xFF292E37),
    red = Color(0xFFC8102E),
    redBright = Color(0xFFE12B3F),
    redDark = Color(0xFF8C0B21),
    textPrimary = Color(0xFFF7F7F8),
    textSecondary = Color(0xFFA8ABB2),
    textDisabled = Color(0xFF686F79),
    success = Color(0xFF35A36D),
    warning = Color(0xFFD29138),
    info = Color(0xFF3B82F6),
    paperWarm = Color(0xFFE8DDC9),
    paperCool = Color(0xFFD6DADF),
    paperInk = Color(0xFF090B0F),
    paperAction = Color(0xFF8C0B21),
    heroBackground = Color(0xFF090B0F),
    heroText = Color(0xFFF7F7F8),
    heroTextSecondary = Color(0xFFA8ABB2),
    productCardBorder = Color(0xFF292E37),
    productCardBorderWidth = 1f,
)

/** The POS look (packages/pos-design light palette): light canvas, white cards, red lining. */
val PosPalette = GtrPalette(
    style = CustomerStyle.Pos,
    isDark = false,
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFF4F5F7),
    surfaceHigh = Color(0xFFEDEFF2),
    border = Color(0xFFE3E6EB),
    red = Color(0xFFC8102E),
    redBright = Color(0xFFC8102E),
    redDark = Color(0xFF8C0B21),
    textPrimary = Color(0xFF111418),
    textSecondary = Color(0xFF5B6270),
    textDisabled = Color(0xFF9AA3B2),
    success = Color(0xFF12805C),
    warning = Color(0xFFB4690E),
    info = Color(0xFF2563EB),
    paperWarm = Color(0xFFFFFFFF),
    paperCool = Color(0xFFFFFFFF),
    paperInk = Color(0xFF111418),
    paperAction = Color(0xFF8C0B21),
    heroBackground = Color(0xFF0B0C0F),
    heroText = Color(0xFFFFFFFF),
    heroTextSecondary = Color(0xFFC9CED6),
    productCardBorder = Color(0xB3C8102E),
    productCardBorderWidth = 1.5f,
)

/** Illustrated light: warm paper tones around the same artwork; hero stays dark. */
val IllustratedLightPalette = GtrPalette(
    style = CustomerStyle.Illustrated,
    isDark = false,
    background = Color(0xFFF6F3EE),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFBF8F3),
    surfaceSoft = Color(0xFFEFE9E0),
    surfaceHigh = Color(0xFFE6DED2),
    border = Color(0xFFDDD3C4),
    red = Color(0xFFC8102E),
    redBright = Color(0xFFB00E28),
    redDark = Color(0xFF8C0B21),
    textPrimary = Color(0xFF15171C),
    textSecondary = Color(0xFF5F5A52),
    textDisabled = Color(0xFF9E978C),
    success = Color(0xFF1E7A4F),
    warning = Color(0xFFA8650F),
    info = Color(0xFF2563EB),
    paperWarm = Color(0xFFE8DDC9),
    paperCool = Color(0xFFD6DADF),
    paperInk = Color(0xFF15171C),
    paperAction = Color(0xFF8C0B21),
    heroBackground = Color(0xFF090B0F),
    heroText = Color(0xFFF7F7F8),
    heroTextSecondary = Color(0xFFC4C6CC),
    productCardBorder = Color(0xFFDDD3C4),
    productCardBorderWidth = 1f,
)

/** POS dark: the POS design system's dark neutrals (packages/pos-design), red lining kept. */
val PosDarkPalette = GtrPalette(
    style = CustomerStyle.Pos,
    isDark = true,
    background = Color(0xFF12151C),
    surface = Color(0xFF1E2430),
    surfaceRaised = Color(0xFF1E2430),
    surfaceSoft = Color(0xFF282E3D),
    surfaceHigh = Color(0xFF2F3647),
    border = Color(0xFF2A3241),
    red = Color(0xFFC8102E),
    redBright = Color(0xFFE01234),
    redDark = Color(0xFF8C0B21),
    textPrimary = Color(0xFFF4F5F7),
    textSecondary = Color(0xFFA3ABBA),
    textDisabled = Color(0xFF6B7385),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    info = Color(0xFF60A5FA),
    paperWarm = Color(0xFF3A2027),
    paperCool = Color(0xFF282E3D),
    paperInk = Color(0xFFF4F5F7),
    paperAction = Color(0xFFFF8A9A),
    heroBackground = Color(0xFF0A0C0E),
    heroText = Color(0xFFFFFFFF),
    heroTextSecondary = Color(0xFFC9CED6),
    productCardBorder = Color(0xB3E01234),
    productCardBorderWidth = 1.5f,
)

val LocalGtrPalette = staticCompositionLocalOf { IllustratedPalette }

/**
 * Colour tokens of the active customer style. Same names as the original fixed tokens, so every
 * screen follows the style the customer picks without per-screen changes.
 */
object GtrPremiumColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.surface
    val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.surfaceRaised
    val SurfaceSoft: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.surfaceSoft
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.surfaceHigh
    val Border: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.border

    val Red: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.red
    val RedBright: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.redBright
    val RedDark: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.redDark

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.textSecondary
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.textDisabled

    val Success: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.warning
    val Info: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.info

    val PaperWarm: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.paperWarm
    val PaperCool: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.paperCool

    val PaperInk: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.paperInk
    val PaperAction: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.paperAction

    val HeroBackground: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.heroBackground
    val HeroText: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.heroText
    val HeroTextSecondary: Color @Composable @ReadOnlyComposable get() = LocalGtrPalette.current.heroTextSecondary
}

object GtrPremiumDimens {
    val ScreenPadding = 16.dp
    val Gutter = 12.dp

    val TopBarHeight = 72.dp
    val BottomBarHeight = 72.dp

    val HeroHeight = 300.dp
    val VehicleCardHeight = 104.dp

    val CategoryWidth = 76.dp
    val CategoryHeight = 96.dp
    val CategoryArt = 60.dp

    val SupportCardWidth = 164.dp
    val SupportCardHeight = 128.dp

    val ProductCardWidth = 154.dp
    val ProductCardHeight = 248.dp
    val ProductImageHeight = 128.dp

    val PrimaryControlHeight = 48.dp
    val FilterChipHeight = 36.dp
    val PdpMediaHeight = 300.dp
    val CartThumb = 72.dp
}
