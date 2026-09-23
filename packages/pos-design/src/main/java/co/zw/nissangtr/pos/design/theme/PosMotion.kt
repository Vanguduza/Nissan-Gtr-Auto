package co.zw.nissangtr.pos.design.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Motion tiers and spring definitions for Nissan GTR POS (Blueprint §4.9 / SYS-12).
 */
@Immutable
data class PosMotion(
    val isReducedMotion: Boolean = false,
    val m1DurationMs: Int = 100,
    val m2DurationMs: Int = 180,
    val m3DurationMs: Int = 240,
    val pressScale: Float = if (isReducedMotion) 1.0f else 0.98f,
) {
    val m1Easing: Easing = FastOutLinearInEasing
    val m2Easing: Easing = FastOutSlowInEasing
    val m3Easing: Easing = FastOutSlowInEasing

    val m1Spec: TweenSpec<Float> = tween(
        durationMillis = if (isReducedMotion) 0 else m1DurationMs,
        easing = m1Easing
    )

    val m2Spec: TweenSpec<Float> = tween(
        durationMillis = if (isReducedMotion) 0 else m2DurationMs,
        easing = m2Easing
    )

    val m2OffsetSpec: TweenSpec<androidx.compose.ui.unit.IntOffset> = tween(
        durationMillis = if (isReducedMotion) 0 else m2DurationMs,
        easing = m2Easing
    )

    val m3Spec: TweenSpec<Float> = tween(
        durationMillis = if (isReducedMotion) 0 else m3DurationMs,
        easing = m3Easing
    )

    val m3OffsetSpec: TweenSpec<androidx.compose.ui.unit.IntOffset> = tween(
        durationMillis = if (isReducedMotion) 0 else m3DurationMs,
        easing = m3Easing
    )

    val pressSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 900f
    )

    companion object {
        fun resolve(reducedMotion: Boolean): PosMotion = PosMotion(isReducedMotion = reducedMotion)
    }
}
