package co.zw.nissangtr.pos.design.primitives

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme

/**
 * Declared glass capability treatment (Blueprint §5.3 / SYS-20).
 */
enum class GlassTreatment {
    Auto,
    Api31Blur,
    FallbackPre31,
}

/**
 * Declared glass capability pair for Nissan GTR POS (Blueprint §5.3 / SYS-20).
 *
 * API 31+: Backdrop blur 16–20 dp radius · surfacePrimary at 72% · 1 dp borderSubtle · elevation.3
 * Below API 31: No blur · surfacePrimary at 94% over scrim at 32% · 1 dp borderSubtle · elevation.3
 */
@Composable
fun PosGlassSurface(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = PosTheme.shape.lg,
    treatment: GlassTreatment = GlassTreatment.Auto,
    blurRadius: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    val isBlurCapable = when (treatment) {
        GlassTreatment.Auto -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        GlassTreatment.Api31Blur -> true
        GlassTreatment.FallbackPre31 -> false
    }

    val elevation = PosTheme.elevation
    val palette = PosTheme.palette

    val baseModifier = modifier
        .shadow(elevation = elevation.elevation3, shape = shape)
        .clip(shape)

    if (isBlurCapable) {
        Box(
            modifier = baseModifier
                .blur(blurRadius)
                .background(palette.surfacePrimary.copy(alpha = 0.72f))
                .border(elevation.borderSubtleWidth, palette.borderSubtle, shape)
        ) {
            content()
        }
    } else {
        // Fallback: surfacePrimary at 94% over scrim at 32%
        Box(
            modifier = baseModifier
                .background(palette.scrim.copy(alpha = 0.32f))
                .background(palette.surfacePrimary.copy(alpha = 0.94f))
                .border(elevation.borderSubtleWidth, palette.borderSubtle, shape)
        ) {
            content()
        }
    }
}
