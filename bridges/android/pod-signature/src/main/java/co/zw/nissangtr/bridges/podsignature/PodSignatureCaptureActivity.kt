package co.zw.nissangtr.bridges.podsignature

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Native signature pad Activity for POD — no WebView.
 * Returns local PNG path extras via setResult — no network.
 */
class PodSignatureCaptureActivity : AppCompatActivity() {

    private lateinit var pad: SignaturePadView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleText = intent.getStringExtra(EXTRA_TITLE)
            ?: getString(R.string.gtr_pod_signature_title)
        val strokeDp = intent.getFloatExtra(
            EXTRA_STROKE_WIDTH_DP,
            SignaturePadView.DEFAULT_STROKE_DP,
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        val heading = TextView(this).apply {
            text = titleText
            textSize = 20f
            setPadding(0, 0, 0, 8)
        }
        val hint = TextView(this).apply {
            text = getString(R.string.gtr_pod_signature_hint)
            setPadding(0, 0, 0, 16)
        }

        pad = SignaturePadView(this).apply {
            setStrokeWidthDp(strokeDp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply {
                bottomMargin = 16
            }
            // Light border via padding frame
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = 2f
        }

        val padFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply { bottomMargin = 16 }
            setBackgroundColor(0xFFCCCCCC.toInt())
            setPadding(2, 2, 2, 2)
            addView(
                pad,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val cancel = Button(this).apply {
            text = getString(R.string.gtr_pod_signature_cancel)
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        val clear = Button(this).apply {
            text = getString(R.string.gtr_pod_signature_clear)
            setOnClickListener { pad.clear() }
        }
        val confirm = Button(this).apply {
            text = getString(R.string.gtr_pod_signature_confirm)
            setOnClickListener { confirmSignature() }
        }
        buttons.addView(cancel)
        buttons.addView(clear)
        buttons.addView(confirm)

        root.addView(heading)
        root.addView(hint)
        root.addView(padFrame)
        root.addView(buttons)
        setContentView(root)
        title = titleText
    }

    private fun confirmSignature() {
        if (!pad.hasInk()) {
            Toast.makeText(
                this,
                getString(R.string.gtr_pod_signature_empty),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val bitmap = pad.toBitmap()
        val outDir = File(cacheDir, "pod-signatures").apply { mkdirs() }
        val outFile = File(outDir, "sig_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        val data = Intent().apply {
            putExtra(EXTRA_LOCAL_PATH, outFile.absolutePath)
            putExtra(EXTRA_MIME_TYPE, MIME_PNG)
            putExtra(EXTRA_CAPTURED_AT, Instant.now().toString())
        }
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_TITLE: String =
            "co.zw.nissangtr.bridges.podsignature.EXTRA_TITLE"
        const val EXTRA_STROKE_WIDTH_DP: String =
            "co.zw.nissangtr.bridges.podsignature.EXTRA_STROKE_WIDTH_DP"
        const val EXTRA_LOCAL_PATH: String =
            "co.zw.nissangtr.bridges.podsignature.EXTRA_LOCAL_PATH"
        const val EXTRA_MIME_TYPE: String =
            "co.zw.nissangtr.bridges.podsignature.EXTRA_MIME_TYPE"
        const val EXTRA_CAPTURED_AT: String =
            "co.zw.nissangtr.bridges.podsignature.EXTRA_CAPTURED_AT"
        const val MIME_PNG: String = "image/png"
    }
}
