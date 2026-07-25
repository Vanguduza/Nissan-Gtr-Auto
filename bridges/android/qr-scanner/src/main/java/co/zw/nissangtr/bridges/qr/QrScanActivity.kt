package co.zw.nissangtr.bridges.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen CameraX preview + ML Kit QR decode.
 * Returns [EXTRA_RAW_VALUE] / [EXTRA_SCANNED_AT] via setResult — no network.
 */
@androidx.camera.core.ExperimentalGetImage
class QrScanActivity : AppCompatActivity() {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val delivered = AtomicBoolean(false)
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val root = FrameLayout(this)
        val previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val hint = TextView(this).apply {
            text = getString(R.string.gtr_qr_scan_hint)
            setPadding(32, 48, 32, 16)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x66000000)
        }
        val cancel = Button(this).apply {
            text = getString(R.string.gtr_qr_scan_cancel)
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 64
            }
        }
        root.addView(previewView)
        root.addView(hint)
        root.addView(cancel)
        setContentView(root)
        title = getString(R.string.gtr_qr_scan_title)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                bindCamera(cameraProvider, previewView)
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun bindCamera(cameraProvider: ProcessCameraProvider, previewView: PreviewView) {
        val preview = Preview.Builder().build().also { p ->
            p.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || delivered.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (raw != null && delivered.compareAndSet(false, true)) {
                                setResult(
                                    RESULT_OK,
                                    Intent().apply {
                                        putExtra(EXTRA_RAW_VALUE, raw)
                                        putExtra(EXTRA_SCANNED_AT, Instant.now().toString())
                                    },
                                )
                                finish()
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
            }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        scanner.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RAW_VALUE: String = "co.zw.nissangtr.bridges.qr.RAW_VALUE"
        const val EXTRA_SCANNED_AT: String = "co.zw.nissangtr.bridges.qr.SCANNED_AT"
    }
}
