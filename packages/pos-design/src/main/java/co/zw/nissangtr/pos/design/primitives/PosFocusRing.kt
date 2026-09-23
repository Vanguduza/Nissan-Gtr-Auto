package co.zw.nissangtr.pos.design.primitives

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import co.zw.nissangtr.pos.design.theme.PosTheme

/**
 * Focus contract and visible focus ring modifier for Nissan GTR POS (Blueprint §5.10 / SYS-19).
 * Renders a mandatory 1.5 dp borderFocus ring on focus.
 */
fun Modifier.posFocusRing(
    shape: CornerBasedShape? = null,
    isFocusedOverride: Boolean? = null,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val focused = isFocusedOverride ?: isFocused
    val actualShape = shape ?: PosTheme.shape.sm
    val borderFocusColor = PosTheme.palette.borderFocus
    val borderWidth = PosTheme.elevation.borderStrongWidth

    this
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (focused) {
                Modifier.border(borderWidth, borderFocusColor, actualShape)
            } else {
                Modifier
            }
        )
}
