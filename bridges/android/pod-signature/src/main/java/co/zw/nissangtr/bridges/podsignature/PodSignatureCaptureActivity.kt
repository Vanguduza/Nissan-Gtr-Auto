package co.zw.nissangtr.bridges.podsignature

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Full-screen Compose Canvas signature pad for POD — no WebView.
 * Returns local PNG path extras via setResult — no network.
 */
class PodSignatureCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titleText = intent.getStringExtra(EXTRA_TITLE)
            ?: getString(R.string.gtr_pod_signature_title)
        val strokeDp = intent.getFloatExtra(
            EXTRA_STROKE_WIDTH_DP,
            SignaturePadView.DEFAULT_STROKE_DP,
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val padState = rememberComposeSignaturePadState()
                    val density = LocalDensity.current
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(titleText, style = MaterialTheme.typography.titleLarge)
                        Text(
                            getString(R.string.gtr_pod_signature_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ComposeSignaturePad(
                            state = padState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            height = null,
                            strokeWidthDp = strokeDp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    setResult(RESULT_CANCELED)
                                    finish()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(getString(R.string.gtr_pod_signature_cancel)) }
                            OutlinedButton(
                                onClick = { padState.clear() },
                                modifier = Modifier.weight(1f),
                            ) { Text(getString(R.string.gtr_pod_signature_clear)) }
                            Button(
                                onClick = {
                                    if (!padState.hasInk) {
                                        Toast.makeText(
                                            this@PodSignatureCaptureActivity,
                                            getString(R.string.gtr_pod_signature_empty),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@Button
                                    }
                                    val strokePx = with(density) { strokeDp.dp.toPx() }
                                    val result = padState.toPngFile(
                                        outDir = File(cacheDir, "pod-signatures"),
                                        strokeWidthPx = strokePx,
                                    )
                                    setResult(
                                        RESULT_OK,
                                        android.content.Intent().apply {
                                            putExtra(EXTRA_LOCAL_PATH, result.localPath)
                                            putExtra(EXTRA_MIME_TYPE, result.mimeType)
                                            putExtra(EXTRA_CAPTURED_AT, result.capturedAt)
                                        },
                                    )
                                    finish()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(getString(R.string.gtr_pod_signature_confirm)) }
                        }
                    }
                }
            }
        }
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
