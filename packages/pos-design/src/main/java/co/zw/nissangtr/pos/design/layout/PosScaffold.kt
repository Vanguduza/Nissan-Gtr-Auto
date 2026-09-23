package co.zw.nissangtr.pos.design.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import kotlin.math.floor
import kotlin.math.max

/**
 * Adaptive layout mathematics and derived item count formulas (Blueprint §3.3 / SYS-14).
 */
object PosAdaptiveMath {
    fun clamp(min: Dp, value: Dp, max: Dp): Dp {
        return value.coerceIn(min, max)
    }

    data class DerivedGrid(
        val count: Int,
        val itemWidth: Dp,
    )

    fun deriveLazyRow(
        contentWidth: Dp,
        minItemWidth: Dp,
        maxItemWidth: Dp,
        gap: Dp,
    ): DerivedGrid {
        val totalAvailable = contentWidth.value + gap.value
        val slotWidth = minItemWidth.value + gap.value
        val n = max(1, floor(totalAvailable / slotWidth).toInt())
        val computedWidth = (contentWidth.value - (n - 1) * gap.value) / n
        val clampedWidth = computedWidth.coerceIn(minItemWidth.value, maxItemWidth.value).dp
        return DerivedGrid(count = n, itemWidth = clampedWidth)
    }
}

/**
 * Responsive layout container implementing Blueprint §3.3 layout laws (SYS-14).
 * Enforces priority order: Cart Pane -> Nav Rail -> Discovery Canvas.
 */
@Composable
fun PosScaffold(
    modifier: Modifier = Modifier,
    rail: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    cartPane: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    snackbarHost: (@Composable () -> Unit)? = null,
    content: @Composable (contentWidth: Dp) -> Unit,
) {
    val geometry = PosTheme.geometry
    val palette = PosTheme.palette

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvas),
    ) {
        val availableWidth = maxWidth

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // 1. Navigation Rail
                if (rail != null && geometry.railWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width(geometry.railWidth)
                            .fillMaxHeight()
                            .background(palette.navBackground),
                    ) {
                        rail()
                    }
                }

                // 2. Central Discovery Canvas & Header
                val remainingForCenterAndCart = availableWidth - geometry.railWidth
                val actualCartWidth = if (geometry.isCartPersistent && cartPane != null) {
                    PosAdaptiveMath.clamp(320.dp, remainingForCenterAndCart * 0.28f, 420.dp)
                } else {
                    0.dp
                }
                val canvasWidth = remainingForCenterAndCart - actualCartWidth

                Column(
                    modifier = Modifier
                        .width(canvasWidth)
                        .fillMaxHeight(),
                ) {
                    if (header != null) {
                        header()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        content(canvasWidth)
                    }
                }

                // 3. Cart Pane
                if (cartPane != null && actualCartWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width(actualCartWidth)
                            .fillMaxHeight()
                            .background(palette.surfacePrimary),
                    ) {
                        cartPane()
                    }
                }
            }

            // Bottom Navigation for Compact Portrait
            if (bottomBar != null && geometry.isBottomNav) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.navBackground),
                ) {
                    bottomBar()
                }
            }
        }

        // Overlay feedback surface at bottom-start of working area
        if (snackbarHost != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(420.dp),
            ) {
                snackbarHost()
            }
        }
    }
}
