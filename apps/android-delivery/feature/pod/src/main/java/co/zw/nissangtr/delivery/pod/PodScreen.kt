package co.zw.nissangtr.delivery.pod

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.ComposeSignaturePad
import co.zw.nissangtr.bridges.podsignature.ComposeSignaturePadState
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.bridges.podsignature.SignaturePadView
import co.zw.nissangtr.bridges.podsignature.rememberComposeSignaturePadState
import co.zw.nissangtr.delivery.design.Slopes
import co.zw.nissangtr.delivery.design.SlopesBanner
import co.zw.nissangtr.delivery.design.SlopesDrawer
import co.zw.nissangtr.delivery.design.neuRaised
import co.zw.nissangtr.delivery.design.SlopesGroup
import co.zw.nissangtr.delivery.design.SlopesIconBadge
import co.zw.nissangtr.delivery.design.SlopesPill
import co.zw.nissangtr.delivery.design.SlopesPrimaryButton
import co.zw.nissangtr.delivery.design.SlopesRow
import co.zw.nissangtr.delivery.design.SlopesSectionHeader
import co.zw.nissangtr.delivery.design.SlopesSegment
import co.zw.nissangtr.delivery.design.SlopesTextField
import co.zw.nissangtr.delivery.design.SlopesTimeline
import co.zw.nissangtr.delivery.design.SlopesTintedButton
import co.zw.nissangtr.delivery.design.SlopesTone
import co.zw.nissangtr.bridges.terminal.IntentCardTerminalBridge
import co.zw.nissangtr.bridges.terminal.SimulatedCardTerminalBridge
import co.zw.nissangtr.delivery.rpc.FakeRpcClient
import co.zw.nissangtr.delivery.rpc.RpcClient
import java.io.File

/**
 * Proof of delivery inside the stop sheet: payment (cash / card on delivery, when due) → photo →
 * signature → customer code → complete.
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
    // Card machine through the bridge only; the fake backend gets the demo machine, never the live one.
    val cardBridge = remember(rpc) {
        if (rpc is FakeRpcClient) {
            SimulatedCardTerminalBridge()
        } else {
            IntentCardTerminalBridge(context).also { b -> context.findActivity()?.let(b::attachActivity) }
        }
    }
    val payVm: DeliveryPaymentViewModel = viewModel(
        key = "pay-$jobId",
        factory = DeliveryPaymentViewModel.factory(context, rpc, cardBridge),
    )
    val pay by payVm.state.collectAsState()
    val padState = rememberComposeSignaturePadState()
    val density = LocalDensity.current

    LaunchedEffect(jobId) {
        vm.bindJob(jobId)
        payVm.bindJob(jobId)
        padState.clear()
    }
    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    Column(modifier.fillMaxWidth()) {
        DeliveryPaymentContent(
            state = pay,
            onRetry = payVm::refresh,
            onMethod = payVm::selectMethod,
            onAmountChange = payVm::onAmountChange,
            onCashNotesChange = payVm::onCashNotesChange,
            onCollectCash = payVm::collectCash,
            onSelectTerminal = payVm::selectTerminal,
            onReloadTerminals = payVm::loadTerminals,
            onPair = payVm::pair,
            onCharge = payVm::chargeCard,
            onAskAgain = payVm::askAgain,
            onFinish = payVm::finishCard,
        )
        PodSectionContent(
            state = state,
            padState = padState,
            paymentDone = if (CodGate.collects(pay.context)) pay.blockingReason == null else null,
            paymentBlock = pay.blockingReason,
            onCapturePhoto = vm::capturePhoto,
            onConfirmSignature = {
                if (padState.hasInk) {
                    val strokePx = with(density) { SignaturePadView.DEFAULT_STROKE_DP.dp.toPx() }
                    val result = padState.toPngFile(
                        outDir = File(context.cacheDir, "pod-signatures"),
                        strokeWidthPx = strokePx,
                    )
                    vm.acceptSignature(result)
                    padState.clear()
                }
            },
            onFullScreenSignature = vm::captureSignatureFullscreen,
            onResign = {
                vm.clearSignature()
                padState.clear()
            },
            onSendCode = vm::generateOtp,
            onCodeChange = vm::onOtpChange,
            onVerifyCode = vm::verifyOtp,
            onNotesChange = vm::onNotesChange,
            onSubmit = { vm.submitPod(paymentBlock = pay.blockingReason) },
            onFlushQueue = vm::flushNow,
        )
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Stateless proof-of-delivery body (rendered by [PodSection]; also used by screenshot tests). */
@Composable
fun PodSectionContent(
    state: PodUiState,
    padState: ComposeSignaturePadState,
    onCapturePhoto: () -> Unit,
    onConfirmSignature: () -> Unit,
    onFullScreenSignature: () -> Unit,
    onResign: () -> Unit,
    onSendCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onNotesChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFlushQueue: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null: nothing to collect on this stop; false: payment still due. */
    paymentDone: Boolean? = null,
    /** Why payment holds completion (shown on the button), or null. */
    paymentBlock: String? = null,
) {
    val c = Slopes.colors
    val podReady = PodEvidenceGate.canSubmit(
        state.photoLocalPath,
        state.signatureLocalPath,
        state.otpCode,
        state.otpVerified,
    ) && paymentBlock == null
    val steps = listOfNotNull(
        paymentDone?.let { "Paid" to it },
    ) + listOf(
        "Photo" to (state.photoLocalPath != null),
        "Signature" to (state.signatureLocalPath != null),
        "Code" to state.otpVerified,
        "Done" to state.completed,
    )
    val nextStep = steps.indexOfFirst { !it.second }

    Column(modifier.fillMaxWidth()) {
        if (paymentDone != null) Spacer(Modifier.height(20.dp))
        SlopesTimeline(
            segments = steps.mapIndexed { i, (label, done) ->
                SlopesSegment(
                    weight = 1f,
                    color = when {
                        done -> c.success
                        i == nextStep -> c.accent
                        else -> c.fill
                    },
                    label = label,
                )
            },
        )

        // 1 · Photo
        SlopesSectionHeader("1  Photo")
        if (state.photoLocalPath != null) {
            LocalImagePreview(path = state.photoLocalPath, heightDp = 170, contentDescription = "Delivery photo")
            Spacer(Modifier.height(10.dp))
            SlopesTintedButton(
                "Retake photo",
                onCapturePhoto,
                enabled = !state.busy,
                icon = Icons.Filled.CameraAlt,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        } else {
            SlopesGroup {
                SlopesRow(
                    title = "Take a photo",
                    subtitle = "Show the parts at the drop-off",
                    leading = { SlopesIconBadge(Icons.Filled.CameraAlt) },
                    onClick = if (state.busy) null else onCapturePhoto,
                    divider = false,
                )
            }
        }

        // 2 · Signature
        SlopesSectionHeader(
            "2  Customer signature",
            action = if (state.signatureLocalPath == null) "Full screen" else null,
            onAction = onFullScreenSignature,
        )
        if (state.signatureLocalPath == null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .neuRaised()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White),
            ) {
                ComposeSignaturePad(
                    state = padState,
                    height = 168.dp,
                    strokeWidthDp = SignaturePadView.DEFAULT_STROKE_DP,
                    borderColor = Color.Transparent,
                )
                if (!padState.hasInk) {
                    Text(
                        "Sign here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFA3A3AB),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SlopesTintedButton(
                    "Clear",
                    { padState.clear() },
                    enabled = !state.busy && padState.hasInk,
                    modifier = Modifier.weight(1f),
                )
                SlopesPrimaryButton(
                    "Use signature",
                    onConfirmSignature,
                    enabled = !state.busy && padState.hasInk,
                    icon = Icons.Filled.Draw,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            LocalImagePreview(
                path = state.signatureLocalPath,
                heightDp = 110,
                contentDescription = "Customer signature",
                fit = true,
                background = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            SlopesTintedButton("Sign again", onResign, enabled = !state.busy, modifier = Modifier.padding(horizontal = 20.dp))
        }

        // 3 · Customer code
        SlopesSectionHeader("3  Customer code")
        SlopesGroup {
            SlopesRow(
                title = if (state.otpGenerated) "Code sent — send again" else "Send a code to the customer",
                subtitle = "They read it back to you at the door",
                leading = { SlopesIconBadge(Icons.Filled.Sms) },
                onClick = if (state.busy) null else onSendCode,
                divider = false,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Bottom,
        ) {
            SlopesTextField(
                value = state.otpCode,
                onValueChange = onCodeChange,
                label = "Code",
                placeholder = "6 digits",
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            if (state.otpVerified) {
                Box(Modifier.padding(bottom = 14.dp)) { SlopesPill("Verified", c.success) }
            } else {
                SlopesTintedButton(
                    "Verify",
                    onVerifyCode,
                    enabled = !state.busy && state.otpCode.isNotBlank(),
                    icon = Icons.Filled.Password,
                    modifier = Modifier.weight(0.8f),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        SlopesDrawer(
            title = "Add a note",
            summary = state.notes.ifBlank { "Optional — e.g. left with reception" },
            icon = Icons.Filled.EditNote,
        ) {
            SlopesTextField(
                value = state.notes,
                onValueChange = onNotesChange,
                label = "Note for dispatch",
                placeholder = "Left with reception, gate code…",
                singleLine = false,
                enabled = !state.busy,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        SlopesPrimaryButton(
            label = when {
                state.busy -> "Completing…"
                paymentBlock != null -> paymentBlock
                podReady -> "Complete delivery"
                else -> "Finish the steps above to complete"
            },
            onClick = onSubmit,
            enabled = !state.busy && podReady,
            icon = if (podReady) Icons.Filled.CheckCircle else null,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        if (state.queuedCount > 0) {
            Spacer(Modifier.height(12.dp))
            SlopesBanner(
                "${state.queuedCount} proof${if (state.queuedCount == 1) "" else "s"} waiting to upload — they send when you are back online.",
                tone = SlopesTone.Warning,
                icon = Icons.Filled.CloudUpload,
                action = "Send now",
                onAction = onFlushQueue,
            )
        }
        state.message?.let {
            Spacer(Modifier.height(12.dp))
            SlopesBanner(it, tone = SlopesTone.Info, icon = Icons.Filled.CheckCircle)
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            SlopesBanner(it, tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem)
        }
    }
}

@Composable
private fun LocalImagePreview(
    path: String,
    heightDp: Int,
    contentDescription: String,
    fit: Boolean = false,
    background: Color = Slopes.colors.fill,
) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
    }
    val shape = RoundedCornerShape(16.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(heightDp.dp)
                .clip(shape)
                .background(background),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
        )
    } else {
        SlopesGroup {
            SlopesRow(
                title = "$contentDescription saved",
                subtitle = "…" + path.takeLast(36),
                leading = { SlopesIconBadge(Icons.Filled.CheckCircle, tint = Slopes.colors.success, container = Slopes.colors.success.copy(alpha = 0.14f)) },
                divider = false,
            )
        }
    }
}
