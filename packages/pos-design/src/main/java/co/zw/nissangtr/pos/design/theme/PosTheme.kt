package co.zw.nissangtr.pos.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPosPalette = staticCompositionLocalOf<PosPalette> {
    error("No PosPalette provided. Have you wrapped your UI in PosTheme?")
}

val LocalPosType = staticCompositionLocalOf<PosType> {
    error("No PosType provided. Have you wrapped your UI in PosTheme?")
}

val LocalPosSpace = staticCompositionLocalOf { PosSpace() }
val LocalPosShape = staticCompositionLocalOf { PosShape() }
val LocalPosElevation = staticCompositionLocalOf { PosElevation() }
val LocalPosMotion = staticCompositionLocalOf { PosMotion() }

val LocalPosGeometry = staticCompositionLocalOf<PosGeometry> {
    error("No PosGeometry provided. Have you wrapped your UI in PosTheme?")
}

val LocalPosDensity = staticCompositionLocalOf { PosDensity.Operational }

/**
 * Top-level theme for Nissan GTR Auto POS (Blueprint §4.1 / SYS-02).
 * Operates purely over CompositionLocals with zero Material visual identity.
 */
@Composable
fun PosTheme(
    windowClass: PosWindowClass,
    density: PosDensity = PosDensity.Operational,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPosPalette provides PosPalette.resolve(darkTheme),
        LocalPosType provides PosType.resolve(windowClass),
        LocalPosSpace provides PosSpace(),
        LocalPosShape provides PosShape(),
        LocalPosElevation provides PosElevation(),
        LocalPosMotion provides PosMotion.resolve(reducedMotion),
        LocalPosGeometry provides PosGeometry.resolve(windowClass),
        LocalPosDensity provides density,
        content = content,
    )
}

/**
 * Direct static accessors for the current PosTheme values.
 */
object PosTheme {
    val palette: PosPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPosPalette.current

    val type: PosType
        @Composable
        @ReadOnlyComposable
        get() = LocalPosType.current

    val space: PosSpace
        @Composable
        @ReadOnlyComposable
        get() = LocalPosSpace.current

    val shape: PosShape
        @Composable
        @ReadOnlyComposable
        get() = LocalPosShape.current

    val elevation: PosElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalPosElevation.current

    val motion: PosMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalPosMotion.current

    val geometry: PosGeometry
        @Composable
        @ReadOnlyComposable
        get() = LocalPosGeometry.current

    val density: PosDensity
        @Composable
        @ReadOnlyComposable
        get() = LocalPosDensity.current
}
