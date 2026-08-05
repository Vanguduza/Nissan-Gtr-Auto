package co.zw.nissangtr.management.hr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.biometricphoto.BiometricPhotoCaptureBridge
import co.zw.nissangtr.bridges.biometricphoto.BiometricPhotoCaptureOptions
import co.zw.nissangtr.bridges.biometricphoto.CameraPermissionStatus
import co.zw.nissangtr.management.rpc.HrGradeOption
import co.zw.nissangtr.management.rpc.HrOnboardingAuthResult
import co.zw.nissangtr.management.rpc.HrOnboardingCompleteResult
import co.zw.nissangtr.management.rpc.HrOnboardingDraft
import co.zw.nissangtr.management.rpc.HrOnboardingStage
import co.zw.nissangtr.management.rpc.HrOnboardingStaffRoles
import co.zw.nissangtr.management.rpc.HrRoleOption
import co.zw.nissangtr.management.rpc.RpcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HrOnboardingUiState(
    val bootLoading: Boolean = true,
    val denied: Boolean = false,
    val canViewSensitive: Boolean = false,
    val busy: Boolean = false,
    val drafts: List<HrOnboardingDraft> = emptyList(),
    val grades: List<HrGradeOption> = emptyList(),
    val roles: List<HrRoleOption> = emptyList(),
    val draftId: String? = null,
    val stage: HrOnboardingStage = HrOnboardingStage.PERSONAL,
    // personal
    val fullName: String = "",
    val dob: String = "",
    val nationalId: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val nextOfKin: String = "",
    val gradeId: String = "",
    val hrRoleId: String = "",
    // banking / health (HR|admin only)
    val bankName: String = "",
    val accountNumber: String = "",
    val branch: String = "",
    val medicalAid: String = "",
    val allergies: String = "",
    val emergencyContact: String = "",
    // photo
    val photoLocalPath: String? = null,
    val photoNote: String = "",
    // contract
    val signatureName: String = "",
    val contractSigned: Boolean = false,
    val completion: HrOnboardingCompleteResult? = null,
    val auth: HrOnboardingAuthResult? = null,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Five-stage HR onboarding — mirrors web wizard RPCs/Edge.
 * Stage 3 photo: [BiometricPhotoCaptureBridge] only (Bridge-First).
 * No ZIMRA / payroll tax.
 */
class HrOnboardingViewModel(
    private val rpc: RpcClient,
    private val photoBridge: BiometricPhotoCaptureBridge,
    private val staffRoles: List<String>,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HrOnboardingUiState(
            canViewSensitive = HrOnboardingStaffRoles.allows(staffRoles),
            denied = !HrOnboardingStaffRoles.allows(staffRoles),
        ),
    )
    val state: StateFlow<HrOnboardingUiState> = _state.asStateFlow()

    init {
        if (!_state.value.denied) refresh()
        else _state.update { it.copy(bootLoading = false) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(bootLoading = true, error = null) }
            try {
                val drafts = rpc.listHrOnboardingDrafts()
                val grades = rpc.listHrGrades()
                val roles = rpc.listHrRoles()
                _state.update {
                    it.copy(
                        bootLoading = false,
                        drafts = drafts,
                        grades = grades,
                        roles = roles,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(bootLoading = false, error = e.message ?: "Failed to load onboarding")
                }
            }
        }
    }

    fun startNewDraft() {
        _state.update {
            it.copy(
                draftId = null,
                stage = HrOnboardingStage.PERSONAL,
                fullName = "",
                dob = "",
                nationalId = "",
                email = "",
                phone = "",
                address = "",
                nextOfKin = "",
                gradeId = "",
                hrRoleId = "",
                bankName = "",
                accountNumber = "",
                branch = "",
                medicalAid = "",
                allergies = "",
                emergencyContact = "",
                photoLocalPath = null,
                photoNote = "",
                signatureName = "",
                contractSigned = false,
                completion = null,
                auth = null,
                message = "Started new draft.",
                error = null,
            )
        }
    }

    fun loadDraft(draft: HrOnboardingDraft) {
        val p = draft.payload
        val b = draft.bankingJson.orEmpty()
        val h = draft.healthJson.orEmpty()
        _state.update {
            it.copy(
                draftId = draft.id,
                stage = draft.stage,
                fullName = p["full_name"].orEmpty(),
                dob = p["dob"].orEmpty(),
                nationalId = p["national_id"].orEmpty(),
                email = p["email"].orEmpty(),
                phone = p["phone_e164"].orEmpty(),
                address = p["address"].orEmpty(),
                nextOfKin = p["next_of_kin"].orEmpty(),
                gradeId = p["grade_id"].orEmpty(),
                hrRoleId = p["hr_role_id"].orEmpty(),
                photoNote = p["photo_note"].orEmpty(),
                photoLocalPath = p["photo_local_path"],
                signatureName = p["signature_name"].orEmpty(),
                contractSigned = p["contract_signed"].equals("true", ignoreCase = true),
                bankName = b["bank_name"].orEmpty(),
                accountNumber = b["account_number"].orEmpty(),
                branch = b["branch"].orEmpty(),
                medicalAid = h["medical_aid"].orEmpty(),
                allergies = h["allergies"].orEmpty(),
                emergencyContact = h["emergency_contact"].orEmpty(),
                completion = null,
                auth = null,
                message = "Resumed draft ${draft.id.take(8)}…",
                error = null,
            )
        }
    }

    fun onFullName(v: String) = _state.update { it.copy(fullName = v) }
    fun onDob(v: String) = _state.update { it.copy(dob = v) }
    fun onNationalId(v: String) = _state.update { it.copy(nationalId = v) }
    fun onEmail(v: String) = _state.update { it.copy(email = v) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v) }
    fun onAddress(v: String) = _state.update { it.copy(address = v) }
    fun onNextOfKin(v: String) = _state.update { it.copy(nextOfKin = v) }
    fun onGradeId(v: String) = _state.update { it.copy(gradeId = v) }
    fun onHrRoleId(v: String) = _state.update { it.copy(hrRoleId = v) }
    fun onBankName(v: String) = _state.update { it.copy(bankName = v) }
    fun onAccountNumber(v: String) = _state.update { it.copy(accountNumber = v) }
    fun onBranch(v: String) = _state.update { it.copy(branch = v) }
    fun onMedicalAid(v: String) = _state.update { it.copy(medicalAid = v) }
    fun onAllergies(v: String) = _state.update { it.copy(allergies = v) }
    fun onEmergencyContact(v: String) = _state.update { it.copy(emergencyContact = v) }
    fun onPhotoNote(v: String) = _state.update { it.copy(photoNote = v) }
    fun onSignatureName(v: String) = _state.update { it.copy(signatureName = v) }
    fun onContractSigned(v: Boolean) = _state.update { it.copy(contractSigned = v) }

    fun capturePhoto() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                var perm = photoBridge.getCameraPermissionStatus()
                if (perm != CameraPermissionStatus.GRANTED) {
                    perm = photoBridge.requestCameraPermission()
                }
                if (perm != CameraPermissionStatus.GRANTED) {
                    _state.update {
                        it.copy(busy = false, error = "Camera permission required for profile photo")
                    }
                    return@launch
                }
                val photo = photoBridge.capturePhoto(
                    BiometricPhotoCaptureOptions(
                        title = "Staff profile photo",
                        preferFrontCamera = true,
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        photoLocalPath = photo.localPath,
                        photoNote = it.photoNote.ifBlank { "Captured via biometric-photo bridge" },
                        message = "Photo captured (local path only — upload is app-side).",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "Photo capture failed")
                }
            }
        }
    }

    fun advance() {
        val s = _state.value
        when (s.stage) {
            HrOnboardingStage.PERSONAL -> {
                if (s.fullName.isBlank() || s.email.isBlank() || s.phone.isBlank() || s.gradeId.isBlank()) {
                    _state.update {
                        it.copy(error = "Name, email, phone, and grade are required.")
                    }
                    return
                }
                saveAndGo(HrOnboardingStage.BANKING_HEALTH)
            }
            HrOnboardingStage.BANKING_HEALTH -> {
                if (!s.canViewSensitive) {
                    _state.update { it.copy(error = "HR or admin role required for banking/health.") }
                    return
                }
                if (s.bankName.isBlank() || s.accountNumber.isBlank()) {
                    _state.update { it.copy(error = "Bank name and account number required.") }
                    return
                }
                saveAndGo(HrOnboardingStage.DOCUMENTS)
            }
            HrOnboardingStage.DOCUMENTS -> {
                if (s.photoLocalPath.isNullOrBlank() && s.photoNote.isBlank()) {
                    _state.update {
                        it.copy(error = "Capture a profile photo via Bridge (or add a note).")
                    }
                    return
                }
                saveAndGo(HrOnboardingStage.ROLE_CONTRACT)
            }
            HrOnboardingStage.ROLE_CONTRACT -> {
                if (!s.contractSigned || s.signatureName.isBlank()) {
                    _state.update {
                        it.copy(error = "Capture signature name and confirm contract signed.")
                    }
                    return
                }
                saveAndGo(HrOnboardingStage.CREDENTIALS)
            }
            HrOnboardingStage.CREDENTIALS -> complete()
        }
    }

    private fun buildPayload(s: HrOnboardingUiState): Map<String, String?> = mapOf(
        "full_name" to s.fullName.trim().ifBlank { null },
        "dob" to s.dob.trim().ifBlank { null },
        "national_id" to s.nationalId.trim().ifBlank { null },
        "email" to s.email.trim().ifBlank { null },
        "phone_e164" to s.phone.trim().ifBlank { null },
        "address" to s.address.trim().ifBlank { null },
        "next_of_kin" to s.nextOfKin.trim().ifBlank { null },
        "grade_id" to s.gradeId.trim().ifBlank { null },
        "hr_role_id" to s.hrRoleId.trim().ifBlank { null },
        "photo_note" to s.photoNote.trim().ifBlank { null },
        "photo_local_path" to s.photoLocalPath,
        "photo_file_name" to s.photoLocalPath?.substringAfterLast('/'),
        "contract_signed" to if (s.contractSigned) "true" else "false",
        "signature_name" to s.signatureName.trim().ifBlank { null },
        "photo_bridge" to "Android CameraxBiometricPhotoBridge — no WebView/HTML5 camera",
    )

    private fun saveAndGo(next: HrOnboardingStage) {
        viewModelScope.launch {
            val s = _state.value
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.saveHrOnboardingStage(
                    draftId = s.draftId,
                    stage = next,
                    payload = buildPayload(s),
                    bankingJson = if (s.canViewSensitive) {
                        mapOf(
                            "bank_name" to s.bankName.trim().ifBlank { null },
                            "account_number" to s.accountNumber.trim().ifBlank { null },
                            "branch" to s.branch.trim().ifBlank { null },
                        )
                    } else {
                        null
                    },
                    healthJson = if (s.canViewSensitive) {
                        mapOf(
                            "medical_aid" to s.medicalAid.trim().ifBlank { null },
                            "allergies" to s.allergies.trim().ifBlank { null },
                            "emergency_contact" to s.emergencyContact.trim().ifBlank { null },
                        )
                    } else {
                        null
                    },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        draftId = id,
                        stage = next,
                        message = "Saved · stage ${next.rpcValue}",
                    )
                }
                refreshQuiet()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "Save failed")
                }
            }
        }
    }

    private fun complete() {
        viewModelScope.launch {
            val s = _state.value
            val draftId = s.draftId
            if (draftId.isNullOrBlank()) {
                _state.update { it.copy(error = "Save earlier stages first.") }
                return@launch
            }
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.saveHrOnboardingStage(
                    draftId = draftId,
                    stage = HrOnboardingStage.CREDENTIALS,
                    payload = buildPayload(s),
                    bankingJson = if (s.canViewSensitive) {
                        mapOf(
                            "bank_name" to s.bankName.trim().ifBlank { null },
                            "account_number" to s.accountNumber.trim().ifBlank { null },
                            "branch" to s.branch.trim().ifBlank { null },
                        )
                    } else {
                        null
                    },
                    healthJson = if (s.canViewSensitive) {
                        mapOf(
                            "medical_aid" to s.medicalAid.trim().ifBlank { null },
                            "allergies" to s.allergies.trim().ifBlank { null },
                            "emergency_contact" to s.emergencyContact.trim().ifBlank { null },
                        )
                    } else {
                        null
                    },
                )
                var complete = rpc.completeHrOnboarding(draftId)
                var auth: HrOnboardingAuthResult? = null
                val empId = complete.employeeId
                if (!empId.isNullOrBlank() && complete.userId.isNullOrBlank()) {
                    try {
                        auth = rpc.createHrOnboardingAuthUser(empId)
                        complete = complete.copy(
                            userId = auth.userId,
                            mustChangePassword = auth.mustChangePassword,
                            auth = auth,
                        )
                    } catch (e: Exception) {
                        complete = complete.copy(authError = e.message ?: "auth create failed")
                        _state.update {
                            it.copy(
                                busy = false,
                                completion = complete,
                                auth = null,
                                message =
                                    "Employee created (emp# ${complete.employeeCode}) but auth link failed: ${complete.authError}",
                            )
                        }
                        refreshQuiet()
                        return@launch
                    }
                }
                _state.update {
                    it.copy(
                        busy = false,
                        completion = complete,
                        auth = auth,
                        message = if (auth != null) {
                            "Completed · emp# ${complete.employeeCode} · auth user created; credentials via outbox."
                        } else {
                            "Completed · emp# ${complete.employeeCode} · existing user linked."
                        },
                    )
                }
                refreshQuiet()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "Complete failed")
                }
            }
        }
    }

    private suspend fun refreshQuiet() {
        runCatching {
            val drafts = rpc.listHrOnboardingDrafts()
            _state.update { it.copy(drafts = drafts) }
        }
    }

    companion object {
        fun factory(
            rpc: RpcClient,
            photoBridge: BiometricPhotoCaptureBridge,
            staffRoles: List<String>,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HrOnboardingViewModel(rpc, photoBridge, staffRoles) as T
            }
    }
}
