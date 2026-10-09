package co.zw.nissangtr.pos.design.primitives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import co.zw.nissangtr.pos.design.theme.PosTheme
import kotlinx.coroutines.delay

/**
 * Kind of transient feedback message (Blueprint §5.9 / SYS-17).
 */
enum class FeedbackKind {
    ReversibleAction,
    TransientFailure,
}

/**
 * Feedback notification payload with optional Undo/Retry action.
 */
data class PosFeedbackMessage(
    val message: String,
    val actionLabel: String? = null,
    val kind: FeedbackKind = FeedbackKind.ReversibleAction,
    val onAction: (() -> Unit)? = null,
)

/**
 * Single feedback state holder enforcing a queue depth of 1 (Blueprint §5.9).
 */
@Stable
class PosFeedbackState {
    var currentMessage by mutableStateOf<PosFeedbackMessage?>(null)
        private set

    fun show(message: PosFeedbackMessage) {
        currentMessage = message
    }

    fun dismiss() {
        currentMessage = null
    }
}

@Composable
fun rememberPosFeedbackState(): PosFeedbackState = remember { PosFeedbackState() }

/**
 * Dedicated feedback surface positioned at bottom-start of working area (Blueprint §5.9 / SYS-17).
 */
@Composable
fun PosSnackbarHost(
    state: PosFeedbackState,
    modifier: Modifier = Modifier,
) {
    val message = state.currentMessage

    LaunchedEffect(message) {
        if (message != null) {
            val durationMs = when (message.kind) {
                FeedbackKind.ReversibleAction -> 5000L
                FeedbackKind.TransientFailure -> 8000L
            }
            delay(durationMs)
            state.dismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(PosTheme.motion.m2Spec) + slideInVertically(PosTheme.motion.m2OffsetSpec) { it },
        exit = fadeOut(PosTheme.motion.m2Spec) + slideOutVertically(PosTheme.motion.m2OffsetSpec) { it },
        modifier = modifier,
    ) {
        if (message != null) {
            val palette = PosTheme.palette
            val shape = PosTheme.shape.md
            val elevation = PosTheme.elevation

            Row(
                modifier = Modifier
                    .shadow(elevation.elevation2, shape)
                    .clip(shape)
                    .background(palette.navBackground)
                    .border(elevation.borderSubtleWidth, palette.borderSubtle, shape)
                    .padding(horizontal = PosTheme.space.space4, vertical = PosTheme.space.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PosTheme.space.space3),
            ) {
                Text(
                    text = message.message,
                    style = PosTheme.type.bodyPrimary,
                    color = palette.textOnBrand,
                )
                if (message.actionLabel != null && message.onAction != null) {
                    Text(
                        text = message.actionLabel,
                        style = PosTheme.type.labelAction,
                        color = palette.brandRedPressed,
                        modifier = Modifier
                            .clickable {
                                message.onAction.invoke()
                                state.dismiss()
                            }
                            .padding(
                                horizontal = PosTheme.space.space1,
                                vertical = PosTheme.space.space0_5,
                            ),
                    )
                }
            }
        }
    }
}
