package co.zw.nissangtr.pos.design.theme

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Optical alignment offsets for Nissan GTR POS (Blueprint §5.8 / SYS-13).
 * Explicitly bounded to ±2 dp.
 */
object PosOptical {
    val stepperMinus: DpOffset = DpOffset(0.dp, (-0.5).dp)
    val chevronTrailing: DpOffset = DpOffset(1.dp, 0.dp)
    val iconBesideText: DpOffset = DpOffset(0.dp, (-0.5).dp)
}
