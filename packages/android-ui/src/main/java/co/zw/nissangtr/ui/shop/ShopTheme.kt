package co.zw.nissangtr.ui.shop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import co.zw.nissangtr.ui.theme.GtrDensity
import co.zw.nissangtr.ui.theme.GtrTheme

/**
 * Canonical ShopKit entry — wraps GTR-branded [GtrTheme] (colors / Titillium +
 * Source Sans 3 / KMP shape scale). Presentation primitives live in `Shop*` files
 * forked from Shopping-By-KMP (see `packages/android-ui/README.md`).
 *
 * **Public API freeze (Phase A):** do not rename or remove `Shop*` symbols without
 * a coordinated app migration. File layout may change; symbol names stay stable.
 */
@Composable
fun ShopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    GtrTheme(
        darkTheme = darkTheme,
        density = if (compact) GtrDensity.Compact else GtrDensity.Standard,
        content = content,
    )
}
