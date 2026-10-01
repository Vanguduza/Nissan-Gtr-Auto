package co.zw.nissangtr.pos.design.primitives

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme

/**
 * Soft-UI depth (owner decision D8): a light highlight up-left and a soft shade down-right, drawn
 * behind the element. Used "where necessary" — action buttons, cards and fields on the light canvas —
 * never on the dark rail or hero, and never as decoration on text (Blueprint §5.3 still governs).
 */
fun Modifier.posNeuRaised(
    cornerRadius: Dp = 14.dp,
    distance: Dp = 4.dp,
    blur: Dp = 10.dp,
): Modifier = composed {
    val palette = PosTheme.palette
    neuShadows(palette.neuHighlight, palette.neuShade, cornerRadius, distance, blur)
}

/** Smaller lift for compact controls (steppers, icon buttons, chips). */
fun Modifier.posNeuRaisedSmall(cornerRadius: Dp = 10.dp): Modifier =
    posNeuRaised(cornerRadius = cornerRadius, distance = 2.dp, blur = 6.dp)

private fun Modifier.neuShadows(
    highlight: Color,
    shade: Color,
    cornerRadius: Dp,
    distance: Dp,
    blur: Dp,
): Modifier = drawBehind {
    val d = distance.toPx()
    val r = cornerRadius.toPx()
    val blurPx = blur.toPx()
    drawIntoCanvas { canvas ->
        fun layer(color: Color, dx: Float, dy: Float) {
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            frameworkPaint.color = color.toArgb()
            if (blurPx > 0f) frameworkPaint.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(dx, dy, size.width + dx, size.height + dy, r, r, paint)
        }
        layer(shade, d, d)
        layer(highlight, -d, -d)
    }
}

/** Pressed (inset) treatment for selected segments and active fields. */
@Composable
fun posNeuPressedColor(): Color = PosTheme.palette.neuShade.copy(alpha = 0.35f)
