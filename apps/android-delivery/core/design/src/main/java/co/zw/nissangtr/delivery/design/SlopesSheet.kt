package co.zw.nissangtr.delivery.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slopes' signature layout: a full-bleed map with a draggable sheet over its lower part.
 * The sheet rests at [collapsedFraction] of the height, drags up to near full screen, and
 * hands over to its own scroll once expanded (nested scroll), so the gesture feels continuous.
 *
 * [map] receives the height in px the resting sheet covers, so a map can pad its camera.
 */
@Composable
fun SlopesMapSheetLayout(
    modifier: Modifier = Modifier,
    collapsedFraction: Float = 0.52f,
    expandedTopFraction: Float = 0.08f,
    /** Open the sheet fully on first layout (e.g. a stop opened straight into its proof steps). */
    startExpanded: Boolean = false,
    /** Bump to raise the sheet programmatically (e.g. when the driver taps Complete). */
    expandRequests: Int = 0,
    map: @Composable BoxScope.(obscuredBottomPx: Int) -> Unit,
    mapControls: @Composable ColumnScope.() -> Unit = {},
    topStart: @Composable BoxScope.() -> Unit = {},
    sheetHeader: @Composable ColumnScope.() -> Unit = {},
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    val c = Slopes.colors
    BoxWithConstraints(modifier.fillMaxSize().background(c.background)) {
        val density = LocalDensity.current
        val fullPx = with(density) { maxHeight.toPx() }
        val expandedTop = fullPx * expandedTopFraction
        val collapsedTop = fullPx * (1f - collapsedFraction)
        val top = remember(fullPx) { Animatable(if (startExpanded) expandedTop else collapsedTop) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(collapsedTop, expandedTop) { top.updateBounds(expandedTop, collapsedTop) }
        LaunchedEffect(expandRequests) {
            if (expandRequests > 0) top.animateTo(expandedTop, spring(dampingRatio = 0.86f, stiffness = 420f))
        }

        fun settle(velocity: Float) {
            val mid = (expandedTop + collapsedTop) / 2f
            val target = when {
                velocity < -900f -> expandedTop
                velocity > 900f -> collapsedTop
                top.value < mid -> expandedTop
                else -> collapsedTop
            }
            scope.launch { top.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 420f), velocity) }
        }

        val sheetScroll = rememberScrollState()
        val nested = remember(expandedTop, collapsedTop) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    // Dragging up: grow the sheet before the content scrolls.
                    if (dy < 0f && top.value > expandedTop) {
                        val next = (top.value + dy).coerceAtLeast(expandedTop)
                        val used = next - top.value
                        scope.launch { top.snapTo(next) }
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    // Content is at its top and the finger keeps pulling down: shrink the sheet.
                    if (dy > 0f && top.value < collapsedTop) {
                        val next = (top.value + dy).coerceAtMost(collapsedTop)
                        val used = next - top.value
                        scope.launch { top.snapTo(next) }
                        return Offset(0f, used)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val between = top.value > expandedTop + 1f && top.value < collapsedTop - 1f
                    return if (between) {
                        settle(available.y)
                        available
                    } else {
                        Velocity.Zero
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            map((fullPx - collapsedTop).roundToInt())
        }

        // Map buttons fade away as the sheet rises over them.
        val controlsAlpha = ((top.value - expandedTop) / ((collapsedTop - expandedTop) * 0.35f)).coerceIn(0f, 1f)
        if (controlsAlpha > 0.01f) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 12.dp)
                    .graphicsLayer { alpha = controlsAlpha },
                content = mapControls,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 12.dp),
            content = topStart,
        )

        val dragState = rememberDraggableState { delta ->
            scope.launch { top.snapTo((top.value + delta).coerceIn(expandedTop, collapsedTop)) }
        }
        Column(
            Modifier
                .offset { IntOffset(0, top.value.roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { (fullPx - top.value).toDp() })
                .shadow(18.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(c.background),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val target = if (abs(top.value - collapsedTop) < 2f) expandedTop else collapsedTop
                            scope.launch { top.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 420f)) }
                        }
                        .padding(top = 7.dp, bottom = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SlopesGrabber()
                }
                sheetHeader()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(nested)
                    .verticalScroll(sheetScroll),
            ) {
                sheetContent()
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

@Composable
fun SlopesGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 38.dp, height = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(Slopes.colors.tertiaryLabel.copy(alpha = 0.55f)),
    )
}
