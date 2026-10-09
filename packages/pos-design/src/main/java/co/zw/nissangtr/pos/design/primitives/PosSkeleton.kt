package co.zw.nissangtr.pos.design.primitives

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import co.zw.nissangtr.pos.design.theme.PosDensity
import co.zw.nissangtr.pos.design.theme.PosTheme

/**
 * Exact skeleton primitives (Blueprint §5.7 / SYS-21).
 * Matches destination geometry without layout reflow.
 */
@Composable
fun PosSkeletonBox(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = PosTheme.shape.sm,
) {
    val palette = PosTheme.palette
    val infiniteTransition = rememberInfiniteTransition(label = "PosSkeletonPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "PosSkeletonAlpha",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.borderSubtle.copy(alpha = alpha)),
    )
}

@Composable
fun PosSkeletonCard(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = PosTheme.shape.md,
) {
    PosSkeletonBox(
        modifier = modifier
            .width(width)
            .height(height),
        shape = shape,
    )
}

@Composable
fun PosSkeletonRow(
    density: PosDensity = PosTheme.density,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = PosTheme.shape.sm,
) {
    PosSkeletonBox(
        modifier = modifier
            .fillMaxWidth()
            .height(density.rowHeight),
        shape = shape,
    )
}
