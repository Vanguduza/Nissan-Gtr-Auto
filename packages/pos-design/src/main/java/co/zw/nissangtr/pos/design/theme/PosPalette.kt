package co.zw.nissangtr.pos.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import co.zw.nissangtr.pos.design.tokens.PosTokens

/**
 * 22-slot semantic palette for Nissan GTR POS (Blueprint §4.1.1, §4.2, §5.11 / SYS-03, SYS-04, SYS-05).
 * Resolves strictly from generated PosTokens — zero raw color literals.
 */
@Immutable
data class PosPalette(
    val navBackground: Color,
    val navSurfaceRaised: Color,
    val navActiveFill: Color,
    val brandRed: Color,
    val brandRedPressed: Color,
    val canvas: Color,
    val surfacePrimary: Color,
    val surfaceElevated: Color,
    val heroBackdrop: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val borderFocus: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textOnBrand: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val unknown: Color,
    val offline: Color,
    val scrim: Color,
) {
    companion object {
        fun light(): PosPalette = PosPalette(
            navBackground = PosTokens.ColorTokens.Brand_steel,
            navSurfaceRaised = PosTokens.ColorTokens.Brand_steelLift,
            navActiveFill = PosTokens.ColorTokens.Brand_red,
            brandRed = PosTokens.ColorTokens.Brand_red,
            brandRedPressed = PosTokens.ColorTokens.Brand_redPressed,
            canvas = PosTokens.ColorTokens.Neutral_canvas,
            surfacePrimary = PosTokens.ColorTokens.Neutral_surface,
            surfaceElevated = PosTokens.ColorTokens.Neutral_surfaceElevated,
            heroBackdrop = PosTokens.ColorTokens.Neutral_heroBackdrop,
            borderSubtle = PosTokens.ColorTokens.Neutral_borderSubtle,
            borderStrong = PosTokens.ColorTokens.Neutral_borderStrong,
            borderFocus = PosTokens.ColorTokens.Neutral_borderFocus,
            textPrimary = PosTokens.ColorTokens.Neutral_ink_deep,
            textSecondary = PosTokens.ColorTokens.Neutral_ink_base,
            textMuted = PosTokens.ColorTokens.Neutral_ink_muted,
            textOnBrand = PosTokens.ColorTokens.Brand_primaryInk,
            success = PosTokens.ColorTokens.Status_success,
            warning = PosTokens.ColorTokens.Status_warning,
            error = PosTokens.ColorTokens.Status_error,
            unknown = PosTokens.ColorTokens.Status_unknown,
            offline = PosTokens.ColorTokens.Status_offline,
            scrim = PosTokens.ColorTokens.Neutral_scrim.copy(alpha = 0.32f),
        )

        fun dark(): PosPalette = PosPalette(
            navBackground = PosTokens.ColorTokens.Neutral_dark_heroBackdrop,
            navSurfaceRaised = PosTokens.ColorTokens.Neutral_dark_canvas,
            navActiveFill = PosTokens.ColorTokens.Brand_redPressed,
            brandRed = PosTokens.ColorTokens.Brand_redPressed,
            brandRedPressed = PosTokens.ColorTokens.Brand_red,
            canvas = PosTokens.ColorTokens.Neutral_dark_canvas,
            surfacePrimary = PosTokens.ColorTokens.Neutral_dark_surface,
            surfaceElevated = PosTokens.ColorTokens.Neutral_dark_surfaceElevated,
            heroBackdrop = PosTokens.ColorTokens.Neutral_dark_heroBackdrop,
            borderSubtle = PosTokens.ColorTokens.Neutral_dark_borderSubtle,
            borderStrong = PosTokens.ColorTokens.Neutral_dark_borderStrong,
            borderFocus = PosTokens.ColorTokens.Neutral_dark_borderFocus,
            textPrimary = PosTokens.ColorTokens.Brand_chalk,
            textSecondary = PosTokens.ColorTokens.Brand_silver,
            textMuted = PosTokens.ColorTokens.Brand_silverDim,
            textOnBrand = PosTokens.ColorTokens.Brand_primaryInk,
            success = PosTokens.ColorTokens.Status_dark_success,
            warning = PosTokens.ColorTokens.Status_dark_warning,
            error = PosTokens.ColorTokens.Status_dark_error,
            unknown = PosTokens.ColorTokens.Status_dark_unknown,
            offline = PosTokens.ColorTokens.Status_dark_offline,
            scrim = PosTokens.ColorTokens.Neutral_dark_scrim.copy(alpha = 0.60f),
        )

        fun resolve(darkTheme: Boolean): PosPalette = if (darkTheme) dark() else light()
    }
}
