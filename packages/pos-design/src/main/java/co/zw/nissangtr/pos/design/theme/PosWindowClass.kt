package co.zw.nissangtr.pos.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window class derivation from available window dimensions (Blueprint §3.5 / SYS-15).
 */
enum class PosWindowClass {
    CompactPortrait,
    CompactLandscape,
    Medium,
    Expanded;

    companion object {
        fun derive(widthDp: Dp, heightDp: Dp): PosWindowClass {
            return when {
                widthDp >= 600.dp && heightDp < 480.dp -> CompactLandscape
                widthDp < 600.dp -> CompactPortrait
                widthDp < 1040.dp -> Medium
                else -> Expanded
            }
        }
    }
}

/**
 * Responsive layout geometry derived from PosWindowClass (Blueprint §3.3, §3.5 / SYS-14, SYS-15).
 */
@Immutable
data class PosGeometry(
    val windowClass: PosWindowClass,
    val railWidth: Dp,
    val cartWidth: Dp,
    val isCartPersistent: Boolean,
    val heroHeight: Dp,
    val canvasGutter: Dp = 20.dp,
    val isBottomNav: Boolean = false,
) {
    companion object {
        fun resolve(windowClass: PosWindowClass): PosGeometry = when (windowClass) {
            PosWindowClass.Expanded -> PosGeometry(
                windowClass = windowClass,
                railWidth = 144.dp,
                cartWidth = 360.dp,
                isCartPersistent = true,
                heroHeight = 240.dp
            )
            PosWindowClass.Medium -> PosGeometry(
                windowClass = windowClass,
                railWidth = 88.dp,
                cartWidth = 320.dp,
                isCartPersistent = true,
                heroHeight = 160.dp
            )
            PosWindowClass.CompactLandscape -> PosGeometry(
                windowClass = windowClass,
                railWidth = 88.dp,
                cartWidth = 0.dp,
                isCartPersistent = false,
                heroHeight = 48.dp
            )
            PosWindowClass.CompactPortrait -> PosGeometry(
                windowClass = windowClass,
                railWidth = 0.dp,
                cartWidth = 0.dp,
                isCartPersistent = false,
                heroHeight = 48.dp,
                isBottomNav = true
            )
        }
    }
}
