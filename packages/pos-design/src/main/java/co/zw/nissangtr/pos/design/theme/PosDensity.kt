package co.zw.nissangtr.pos.design.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Density scale for Nissan GTR POS (Blueprint §4.8 / SYS-11).
 * Default: Operational. Touch targets never drop below 48 dp.
 */
enum class PosDensity(
    val rowHeight: Dp,
    val sectionGap: Dp,
    val showSecondaryMetadata: Boolean,
) {
    Comfortable(
        rowHeight = 80.dp,
        sectionGap = 24.dp,
        showSecondaryMetadata = true
    ),
    Operational(
        rowHeight = 72.dp,
        sectionGap = 16.dp,
        showSecondaryMetadata = true
    ),
    Compact(
        rowHeight = 64.dp,
        sectionGap = 12.dp,
        showSecondaryMetadata = false
    );

    val minTouchTarget: Dp = 48.dp
}
