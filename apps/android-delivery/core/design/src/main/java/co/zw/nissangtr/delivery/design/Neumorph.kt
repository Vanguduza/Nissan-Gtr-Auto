package co.zw.nissangtr.delivery.design

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft-UI lift (brand neumorph tokens, owner decision D-015): a highlight up-left and a shade
 * down-right, blurred, drawn behind an element that shares the canvas colour. Same technique as
 * the POS `posNeuRaised`. Used on cards, tiles and buttons — not over the map, never on text.
 */
fun Modifier.neuRaised(
    cornerRadius: Dp = 18.dp,
    distance: Dp = 5.dp,
    blur: Dp = 12.dp,
): Modifier = composed {
    val c = Slopes.colors
    drawBehind {
        val d = distance.toPx()
        val r = cornerRadius.toPx()
        val blurPx = blur.toPx()
        drawIntoCanvas { canvas ->
            fun layer(color: Color, offset: Float) {
                val paint = Paint()
                val fp = paint.asFrameworkPaint()
                fp.color = color.toArgb()
                if (blurPx > 0f) fp.maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(offset, offset, size.width + offset, size.height + offset, r, r, paint)
            }
            layer(c.neuShade, d)
            layer(c.neuHighlight, -d)
        }
    }
}

/** Smaller lift for compact controls (round buttons, segmented thumbs, small tiles). */
fun Modifier.neuRaisedSmall(cornerRadius: Dp = 12.dp): Modifier =
    neuRaised(cornerRadius = cornerRadius, distance = 2.5.dp, blur = 6.dp)

/** Pressed-in look for tracks and fields: inner shade top-left, inner highlight bottom-right. */
fun Modifier.neuInset(cornerRadius: Dp = 12.dp, depth: Dp = 3.dp): Modifier = composed {
    val c = Slopes.colors
    drawWithContent {
        drawContent()
        val r = cornerRadius.toPx()
        val d = depth.toPx()
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
        clipPath(clip) {
            drawIntoCanvas { canvas ->
                fun edge(color: Color, offset: Float) {
                    val paint = Paint()
                    val fp = paint.asFrameworkPaint()
                    fp.color = color.toArgb()
                    fp.style = android.graphics.Paint.Style.STROKE
                    fp.strokeWidth = d * 2f
                    fp.maskFilter = BlurMaskFilter(d * 1.6f, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawRoundRect(offset - d, offset - d, size.width + offset + d, size.height + offset + d, r + d, r + d, paint)
                }
                edge(c.neuShade, d * 0.9f)
                edge(c.neuHighlight.copy(alpha = if (c.isDark) 0.6f else 0.9f), -d * 0.9f)
            }
        }
    }
}
