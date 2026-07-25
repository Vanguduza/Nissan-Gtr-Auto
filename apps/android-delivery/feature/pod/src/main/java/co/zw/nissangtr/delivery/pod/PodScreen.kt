package co.zw.nissangtr.delivery.pod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.delivery.rpc.RpcClient

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

    LaunchedEffect(jobId) {
        vm.bindJob(jobId)
    }
    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Proof of delivery", style = MaterialTheme.typography.titleMedium)
        Text(
            "Photo + signature via bridges. OTP required before complete. No ZIMRA.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = vm::capturePhoto,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.photoLocalPath != null) "Retake photo"
                else "Capture photo",
            )
        }
        state.photoLocalPath?.let {
            Text("Photo: …${it.takeLast(40)}", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = vm::captureSignature,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.signatureLocalPath != null) "Recapture signature"
                else "Capture signature",
            )
        }
        state.signatureLocalPath?.let {
            Text("Signature: …${it.takeLast(40)}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = vm::generateOtp,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Generate OTP for customer") }
        OutlinedTextField(
            value = state.otpCode,
            onValueChange = vm::onOtpChange,
            label = { Text("OTP code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedButton(
            onClick = vm::verifyOtp,
            enabled = !state.busy && state.otpCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.otpVerified) "OTP verified ✓" else "Verify OTP")
        }
        OutlinedTextField(
            value = state.notes,
            onValueChange = vm::onNotesChange,
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        Button(
            onClick = vm::submitPod,
            enabled = !state.busy &&
                state.photoLocalPath != null &&
                state.signatureLocalPath != null &&
                state.otpVerified,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.busy) "Submitting…" else "Submit POD & complete") }
        if (state.queuedCount > 0) {
            Text(
                "Offline POD queue: ${state.queuedCount}",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = vm::flushNow, modifier = Modifier.fillMaxWidth()) {
                Text("Flush POD queue")
            }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
