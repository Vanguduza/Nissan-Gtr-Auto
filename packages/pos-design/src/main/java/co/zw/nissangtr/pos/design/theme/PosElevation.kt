package co.zw.nissangtr.pos.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation and border tokens for Nissan GTR POS (Blueprint §4.5 / SYS-08).
 */
@Immutable
data class PosElevation(
    val elevation0: Dp = 0.dp,
    val elevation1: Dp = 1.dp,
    val elevation2: Dp = 2.dp,
    val elevation3: Dp = 8.dp,
    val borderSubtleWidth: Dp = 1.dp,
    val borderStrongWidth: Dp = 1.5.dp,
)
