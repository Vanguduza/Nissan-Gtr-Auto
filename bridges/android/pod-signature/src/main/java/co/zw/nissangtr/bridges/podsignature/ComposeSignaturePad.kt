package co.zw.nissangtr.bridges.podsignature

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Compose Canvas ink pad for POD customer signatures — no WebView.
 * Host calls [ComposeSignaturePadState.toPngFile] after confirm.
 */
class ComposeSignaturePadState {
    private val strokes = mutableStateListOf<List<Offset>>()
    var currentStroke by mutableStateOf<List<Offset>>(emptyList())
        private set
    var size: IntSize by mutableStateOf(IntSize.Zero)
        private set

    val hasInk: Boolean
        get() = strokes.isNotEmpty() || currentStroke.isNotEmpty()

    /** Committed strokes (for rasterization). */
    val committedStrokes: List<List<Offset>>
        get() = strokes.toList()

    fun clear() {
        strokes.clear()
        currentStroke = emptyList()
    }

    fun onSize(s: IntSize) {
        size = s
    }

    fun start(offset: Offset) {
        currentStroke = listOf(offset)
    }

    fun drag(offset: Offset) {
        currentStroke = currentStroke + offset
    }

    fun end() {
        if (currentStroke.isNotEmpty()) {
            strokes.add(currentStroke)
        }
        currentStroke = emptyList()
    }

    /**
     * Rasterize strokes to a white PNG under [outDir].
     * @return [PodCaptureResult] ready for Storage upload + submit_delivery_pod
     */
    fun toPngFile(outDir: File, strokeWidthPx: Float): PodCaptureResult {
        require(hasInk) { "Signature pad is empty" }
        val w = size.width.coerceAtLeast(1)
        val h = size.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = strokeWidthPx
        }
        fun drawStroke(points: List<Offset>) {
            if (points.isEmpty()) return
            val path = android.graphics.Path()
            path.moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            canvas.drawPath(path, paint)
        }
        for (s in strokes) drawStroke(s)
        drawStroke(currentStroke)
        outDir.mkdirs()
        val outFile = File(outDir, "sig_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return PodCaptureResult(
            localPath = outFile.absolutePath,
            mimeType = "image/png",
            capturedAt = Instant.now().toString(),
        )
    }
}

@Composable
fun rememberComposeSignaturePadState(): ComposeSignaturePadState =
    remember { ComposeSignaturePadState() }

@Composable
fun ComposeSignaturePad(
    state: ComposeSignaturePadState,
    modifier: Modifier = Modifier,
    /** Fixed height; null = fill [modifier] size (e.g. Column weight). */
    height: Dp? = 180.dp,
    strokeWidthDp: Float = SignaturePadView.DEFAULT_STROKE_DP,
    borderColor: Color = Color(0xFFCCCCCC),
) {
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidthDp.dp.toPx() }
    // Read observable state so Canvas redraws on ink changes.
    val committed = state.committedStrokes
    val current = state.currentStroke

    val sized = if (height != null) {
        modifier.fillMaxWidth().height(height)
    } else {
        modifier.fillMaxSize()
    }

    Box(
        modifier = sized
            .border(1.dp, borderColor)
            .background(Color.White)
            .onSizeChanged { state.onSize(it) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { state.start(it) },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.end() },
                    onDrag = { change, _ ->
                        change.consume()
                        state.drag(change.position)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            fun Path.fromOffsets(points: List<Offset>): Path {
                if (points.isEmpty()) return this
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
                return this
            }
            for (points in committed) {
                drawPath(path = Path().fromOffsets(points), color = Color.Black, style = stroke)
            }
            if (current.isNotEmpty()) {
                drawPath(path = Path().fromOffsets(current), color = Color.Black, style = stroke)
            }
        }
    }
}
