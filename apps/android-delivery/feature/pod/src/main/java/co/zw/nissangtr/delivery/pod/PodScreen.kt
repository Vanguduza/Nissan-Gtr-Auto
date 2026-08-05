package co.zw.nissangtr.delivery.pod

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.ComposeSignaturePad
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.bridges.podsignature.SignaturePadView
import co.zw.nissangtr.bridges.podsignature.rememberComposeSignaturePadState
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopOutlinedActionRow
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.shop.ShopStepProgress
import co.zw.nissangtr.ui.theme.GtrColors
import java.io.File

/**
 * Proof of delivery — Shopping-By-KMP checkout-step density inside stop detail.
 * Touch signature (inline Canvas + full-screen [PodSignatureCaptureActivity]) → PNG → Storage →
 * [submit_delivery_pod]. Bridge-First camera/signature only; no ZIMRA.
 */
@Composable
fun PodSection(
    rpc: RpcClient,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    jobId: String,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: PodViewModel = viewModel(
        key = "pod-$jobId",
        factory = PodViewModel.factory(rpc, camera, signature, context),
    )
    val state by vm.state.collectAsState()
    val padState = rememberComposeSignaturePadState()
    val density = LocalDensity.current

    LaunchedEffect(jobId) {
        vm.bindJob(jobId)
        padState.clear()
    }
    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    val podReady = state.photoLocalPath != null && state.signatureLocalPath != null && state.otpVerified
    val completedSteps = listOf(
        state.photoLocalPath != null,
        state.signatureLocalPath != null,
        state.otpVerified,
        state.completed,
    ).count { it }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShopSectionHeader(title = "Proof of delivery", actionLabel = null)
        Text(
            "Photo + customer touch signature. OTP required. No ZIMRA.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShopStepProgress(
            steps = listOf("Photo", "Sign", "OTP", "Submit"),
            completedCount = completedSteps,
        )

        ShopPrimaryButton(
            label = if (state.photoLocalPath != null) "Retake photo" else "1 · Capture photo",
            onClick = vm::capturePhoto,
            enabled = !state.busy,
        )
        state.photoLocalPath?.let { path ->
            LocalImagePreview(path = path, heightDp = 140, contentDescription = "POD photo")
        }

        ShopSectionHeader(title = "2 · Customer signature", actionLabel = null)
        Text(
            "Ask the customer to sign below confirming receipt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.signatureLocalPath == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
            ) {
                ComposeSignaturePad(
                    state = padState,
                    height = 160.dp,
                    strokeWidthDp = SignaturePadView.DEFAULT_STROKE_DP,
                )
            }
            ShopOutlinedActionRow {
                ShopSecondaryButton(
                    label = "Clear",
                    onClick = { padState.clear() },
                    enabled = !state.busy && padState.hasInk,
                    modifier = Modifier.weight(1f),
                )
                ShopPrimaryButton(
                    label = "Confirm sign",
                    onClick = {
                        if (!padState.hasInk) return@ShopPrimaryButton
                        val strokePx = with(density) {
                            SignaturePadView.DEFAULT_STROKE_DP.dp.toPx()
                        }
                        val result = padState.toPngFile(
                            outDir = File(context.cacheDir, "pod-signatures"),
                            strokeWidthPx = strokePx,
                        )
                        vm.acceptSignature(result)
                        padState.clear()
                    },
                    enabled = !state.busy && padState.hasInk,
                    modifier = Modifier.weight(1f),
                )
            }
            ShopSecondaryButton(
                label = "Full-screen signature pad",
                onClick = vm::captureSignatureFullscreen,
                enabled = !state.busy,
            )
        } else {
            LocalImagePreview(
                path = state.signatureLocalPath!!,
                heightDp = 100,
                contentDescription = "Customer signature",
                fit = true,
            )
            ShopSecondaryButton(
                label = "Re-sign",
                onClick = {
                    vm.clearSignature()
                    padState.clear()
                },
                enabled = !state.busy,
            )
        }

        ShopSecondaryButton(
            label = "3 · Generate OTP for customer",
            onClick = vm::generateOtp,
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = state.otpCode,
            onValueChange = vm::onOtpChange,
            label = { Text("OTP code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small,
        )
        ShopSecondaryButton(
            label = if (state.otpVerified) "OTP verified ✓" else "Verify OTP",
            onClick = vm::verifyOtp,
            enabled = !state.busy && state.otpCode.isNotBlank(),
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = vm::onNotesChange,
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
            shape = MaterialTheme.shapes.small,
        )
        ShopPrimaryButton(
            label = when {
                state.busy -> "Submitting…"
                podReady -> "4 · Submit POD & complete"
                else -> "4 · Complete steps 1–3 first"
            },
            onClick = vm::submitPod,
            enabled = !state.busy && podReady,
        )
        if (state.queuedCount > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(GtrColors.Warning.copy(alpha = 0.14f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Offline POD queue: ${state.queuedCount} — will sync when back online",
                    style = MaterialTheme.typography.bodySmall,
                    color = GtrColors.Warning,
                )
                ShopSecondaryButton(label = "Flush POD queue", onClick = vm::flushNow)
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LocalImagePreview(
    path: String,
    heightDp: Int,
    contentDescription: String,
    fit: Boolean = false,
) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, GtrColors.Mist, MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
        )
    } else {
        Text(
            "$contentDescription: …${path.takeLast(40)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
