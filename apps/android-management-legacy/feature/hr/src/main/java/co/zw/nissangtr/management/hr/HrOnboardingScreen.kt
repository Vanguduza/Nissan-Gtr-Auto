package co.zw.nissangtr.management.hr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.biometricphoto.BiometricPhotoCaptureBridge
import co.zw.nissangtr.management.rpc.HrOnboardingStage
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopStepProgress

private val WIZARD_STAGES = listOf(
    HrOnboardingStage.PERSONAL to "Personal",
    HrOnboardingStage.BANKING_HEALTH to "Banking & health",
    HrOnboardingStage.DOCUMENTS to "Biometrics photo",
    HrOnboardingStage.ROLE_CONTRACT to "Contract signing",
    HrOnboardingStage.CREDENTIALS to "Completion",
)

/**
 * Five-stage resumable HR onboarding. Photo via Bridge only — no WebView camera.
 * Banking/health fields only when [staffRoles] includes admin|hr.
 */
@Composable
fun HrOnboardingScreen(
    rpc: RpcClient,
    photoBridge: BiometricPhotoCaptureBridge,
    staffRoles: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HrOnboardingViewModel = viewModel(
        factory = HrOnboardingViewModel.factory(rpc, photoBridge, staffRoles),
    ),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "HR Onboarding",
        subtitle = "Five-stage wizard",
        modifier = modifier,
        onBack = onBack,
    ) {
        Text(
            "Five-stage wizard. Profile photo uses biometric-photo Bridge (CameraX). " +
                "No WebView / HTML5 camera. No payroll tax.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.denied -> {
                ShopStaffPanel(title = "Access denied") {
                    Text(
                        "HR or admin role required for onboarding.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            state.bootLoading -> {
                ShopStaffPanel(title = "Loading") {
                    Text("Loading onboarding…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> {
                if (state.drafts.isNotEmpty()) {
                    ShopStaffPanel(title = "Resume draft") {
                        state.drafts.forEach { d ->
                            val name = d.payload["full_name"] ?: "—"
                            ShopSecondaryButton(
                                label = "${d.id.take(8)}… · ${d.stage.rpcValue} · $name",
                                onClick = { viewModel.loadDraft(d) },
                                enabled = !state.busy,
                            )
                        }
                        ShopSecondaryButton(
                            label = "New draft",
                            onClick = viewModel::startNewDraft,
                            enabled = !state.busy,
                        )
                    }
                }

                ShopStaffPanel(title = "Stages") {
                    val stageIndex = WIZARD_STAGES.indexOfFirst { it.first == state.stage }.coerceAtLeast(0)
                    ShopStepProgress(
                        steps = WIZARD_STAGES.map { it.second },
                        completedCount = stageIndex + if (state.stage == HrOnboardingStage.CREDENTIALS) 1 else 0,
                    )

                    when (state.stage) {
                        HrOnboardingStage.PERSONAL -> PersonalStage(state, viewModel)
                        HrOnboardingStage.BANKING_HEALTH -> BankingHealthStage(state, viewModel)
                        HrOnboardingStage.DOCUMENTS -> PhotoStage(state, viewModel)
                        HrOnboardingStage.ROLE_CONTRACT -> ContractStage(state, viewModel)
                        HrOnboardingStage.CREDENTIALS -> CredentialsStage(state)
                    }
                }

                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                ShopPrimaryButton(
                    label = when (state.stage) {
                        HrOnboardingStage.CREDENTIALS -> "Complete onboarding"
                        else -> "Save & continue"
                    },
                    onClick = viewModel::advance,
                    enabled = !state.busy,
                )
            }
        }
    }
}

@Composable
private fun PersonalStage(state: HrOnboardingUiState, vm: HrOnboardingViewModel) {
    Text("Personal information", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.fullName,
        onValueChange = vm::onFullName,
        label = { Text("Full name") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.dob,
        onValueChange = vm::onDob,
        label = { Text("Date of birth (YYYY-MM-DD)") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.nationalId,
        onValueChange = vm::onNationalId,
        label = { Text("National ID") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.email,
        onValueChange = vm::onEmail,
        label = { Text("Email (login)") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.phone,
        onValueChange = vm::onPhone,
        label = { Text("Phone E.164 (login)") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.address,
        onValueChange = vm::onAddress,
        label = { Text("Address") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )
    OutlinedTextField(
        value = state.nextOfKin,
        onValueChange = vm::onNextOfKin,
        label = { Text("Next of kin") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    Text("Grade", style = MaterialTheme.typography.labelLarge)
    state.grades.forEach { g ->
        val selected = state.gradeId == g.id
        Text(
            text = "${if (selected) "✓ " else ""}${g.code} — ${g.title}",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.busy) { vm.onGradeId(g.id) }
                .padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text("Organogram role (optional)", style = MaterialTheme.typography.labelLarge)
    Text(
        text = if (state.hrRoleId.isBlank()) "○ None" else "✓ Clear role",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.busy) { vm.onHrRoleId("") }
            .padding(vertical = 4.dp),
    )
    state.roles.forEach { r ->
        val selected = state.hrRoleId == r.id
        Text(
            text = "${if (selected) "✓ " else ""}${r.title}" +
                (r.department?.let { " ($it)" } ?: ""),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.busy) { vm.onHrRoleId(r.id) }
                .padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun BankingHealthStage(state: HrOnboardingUiState, vm: HrOnboardingViewModel) {
    if (!state.canViewSensitive) {
        Text(
            "Banking & health are HR/admin only.",
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Text("Banking & health (HR/admin only)", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.bankName,
        onValueChange = vm::onBankName,
        label = { Text("Bank") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.accountNumber,
        onValueChange = vm::onAccountNumber,
        label = { Text("Account number") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.branch,
        onValueChange = vm::onBranch,
        label = { Text("Branch") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.medicalAid,
        onValueChange = vm::onMedicalAid,
        label = { Text("Medical aid") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.allergies,
        onValueChange = vm::onAllergies,
        label = { Text("Allergies") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    OutlinedTextField(
        value = state.emergencyContact,
        onValueChange = vm::onEmergencyContact,
        label = { Text("Emergency medical contact") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
}

@Composable
private fun PhotoStage(state: HrOnboardingUiState, vm: HrOnboardingViewModel) {
    Text("Biometrics — profile photo", style = MaterialTheme.typography.titleMedium)
    Text(
        "Capture only (not fingerprint/face matching). Bridge-First CameraX — " +
            "no WebView / HTML5 camera.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ShopSecondaryButton(
        label = "Capture photo (Bridge)",
        onClick = vm::capturePhoto,
        enabled = !state.busy,
    )
    state.photoLocalPath?.let {
        Text("Local path: $it", style = MaterialTheme.typography.bodySmall)
    }
    OutlinedTextField(
        value = state.photoNote,
        onValueChange = vm::onPhotoNote,
        label = { Text("Capture note") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
    )
}

@Composable
private fun ContractStage(state: HrOnboardingUiState, vm: HrOnboardingViewModel) {
    Text("Contract signing", style = MaterialTheme.typography.titleMedium)
    Text(
        "Typed signature stand-in for desk/mobile onboarding.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.signatureName,
        onValueChange = vm::onSignatureName,
        label = { Text("Signer full name") },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.busy,
        singleLine = true,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.contractSigned,
            onCheckedChange = vm::onContractSigned,
            enabled = !state.busy,
        )
        Text("I confirm the role contract was reviewed and signed")
    }
}

@Composable
private fun CredentialsStage(state: HrOnboardingUiState) {
    Text("Completion", style = MaterialTheme.typography.titleMedium)
    Text(
        "Completing assigns emp# GTR{grade}{seq}, then calls hr-onboarding-create-auth " +
            "when no user_id. Temp password stays on the server (outbox only).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.completion?.let { c ->
        Text("Employee #: ${c.employeeCode ?: "—"}")
        Text("Employee id: ${c.employeeId ?: "—"}")
        Text("User id: ${c.userId ?: "(created via Edge / none)"}")
        c.authError?.let {
            Text("Auth error: $it", color = MaterialTheme.colorScheme.error)
        }
    }
    state.auth?.let { a ->
        Text("Auth created=${a.created} must_change=${a.mustChangePassword}")
        a.channels.forEach { ch ->
            Text("  ${ch.channel}: ${ch.status}${ch.error?.let { " ($it)" } ?: ""}")
        }
    }
}
