package co.zw.nissangtr.bridges.biometricphoto



import android.Manifest

import android.content.Intent

import android.content.pm.PackageManager

import android.os.Bundle

import android.view.Gravity

import android.widget.Button

import android.widget.FrameLayout

import android.widget.LinearLayout

import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity

import androidx.camera.core.CameraSelector

import androidx.camera.core.ImageCapture

import androidx.camera.core.ImageCaptureException

import androidx.camera.core.Preview

import androidx.camera.lifecycle.ProcessCameraProvider

import androidx.camera.view.PreviewView

import androidx.core.content.ContextCompat

import java.io.File

import java.time.Instant

import java.util.concurrent.Executors

import java.util.concurrent.atomic.AtomicBoolean



/**

 * Full-screen CameraX preview + still capture for HR onboarding profile photo.

 * Returns local JPEG path extras via setResult — no network / matching.

 */

class BiometricPhotoCaptureActivity : AppCompatActivity() {



    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val captured = AtomicBoolean(false)

    private var imageCapture: ImageCapture? = null

    private var preferFront: Boolean = true



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            != PackageManager.PERMISSION_GRANTED

        ) {

            setResult(RESULT_CANCELED)

            finish()

            return

        }



        preferFront = intent.getBooleanExtra(EXTRA_PREFER_FRONT, true)

        val customTitle = intent.getStringExtra(EXTRA_TITLE)



        val root = FrameLayout(this)

        val previewView = PreviewView(this).apply {

            layoutParams = FrameLayout.LayoutParams(

                FrameLayout.LayoutParams.MATCH_PARENT,

                FrameLayout.LayoutParams.MATCH_PARENT,

            )

        }

        val hint = TextView(this).apply {

            text = getString(R.string.gtr_biometric_photo_hint)

            setPadding(32, 48, 32, 16)

            setTextColor(0xFFFFFFFF.toInt())

            setBackgroundColor(0x66000000)

        }

        val buttons = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            gravity = Gravity.CENTER_HORIZONTAL

            layoutParams = FrameLayout.LayoutParams(

                FrameLayout.LayoutParams.MATCH_PARENT,

                FrameLayout.LayoutParams.WRAP_CONTENT,

            ).apply {

                gravity = Gravity.BOTTOM

                bottomMargin = 64

            }

            setPadding(24, 0, 24, 0)

        }

        val cancel = Button(this).apply {

            text = getString(R.string.gtr_biometric_photo_cancel)

            setOnClickListener {

                setResult(RESULT_CANCELED)

                finish()

            }

        }

        val capture = Button(this).apply {

            text = getString(R.string.gtr_biometric_photo_capture)

            setOnClickListener { takePhoto() }

        }

        buttons.addView(cancel)

        buttons.addView(capture)

        root.addView(previewView)

        root.addView(hint)

        root.addView(buttons)

        setContentView(root)

        title = customTitle?.takeIf { it.isNotBlank() }

            ?: getString(R.string.gtr_biometric_photo_title)



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

        imageCapture = ImageCapture.Builder()

            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

            .build()

        val selector = if (preferFront) {

            CameraSelector.DEFAULT_FRONT_CAMERA

        } else {

            CameraSelector.DEFAULT_BACK_CAMERA

        }

        cameraProvider.unbindAll()

        try {

            cameraProvider.bindToLifecycle(this, selector, preview, imageCapture)

        } catch (_: IllegalArgumentException) {

            // Front unavailable — fall back to back camera.

            cameraProvider.bindToLifecycle(

                this,

                CameraSelector.DEFAULT_BACK_CAMERA,

                preview,

                imageCapture,

            )

        }

    }



    private fun takePhoto() {

        if (!captured.compareAndSet(false, true)) return

        val capture = imageCapture

        if (capture == null) {

            captured.set(false)

            return

        }

        val outDir = File(cacheDir, "hr-biometric-photos").apply { mkdirs() }

        val outFile = File(outDir, "hr_${System.currentTimeMillis()}.jpg")

        val options = ImageCapture.OutputFileOptions.Builder(outFile).build()

        capture.takePicture(

            options,

            cameraExecutor,

            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                    val data = Intent().apply {

                        putExtra(EXTRA_LOCAL_PATH, outFile.absolutePath)

                        putExtra(EXTRA_MIME_TYPE, MIME_JPEG)

                        putExtra(EXTRA_CAPTURED_AT, Instant.now().toString())

                    }

                    setResult(RESULT_OK, data)

                    finish()

                }



                override fun onError(exception: ImageCaptureException) {

                    captured.set(false)

                    setResult(RESULT_CANCELED)

                    finish()

                }

            },

        )

    }



    override fun onDestroy() {

        cameraExecutor.shutdown()

        super.onDestroy()

    }



    companion object {

        const val EXTRA_LOCAL_PATH: String =

            "co.zw.nissangtr.bridges.biometricphoto.EXTRA_LOCAL_PATH"

        const val EXTRA_MIME_TYPE: String =

            "co.zw.nissangtr.bridges.biometricphoto.EXTRA_MIME_TYPE"

        const val EXTRA_CAPTURED_AT: String =

            "co.zw.nissangtr.bridges.biometricphoto.EXTRA_CAPTURED_AT"

        const val EXTRA_PREFER_FRONT: String =

            "co.zw.nissangtr.bridges.biometricphoto.EXTRA_PREFER_FRONT"

        const val EXTRA_TITLE: String =

            "co.zw.nissangtr.bridges.biometricphoto.EXTRA_TITLE"

        const val MIME_JPEG: String = "image/jpeg"

    }

}


