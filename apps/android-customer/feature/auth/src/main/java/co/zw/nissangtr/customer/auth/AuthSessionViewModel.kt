package co.zw.nissangtr.customer.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.AuthEdge
import co.zw.nissangtr.customer.rpc.SupabaseRpcClient
import co.zw.nissangtr.customer.rpc.UserFacingErrors
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthGateState {
    data object Checking : AuthGateState()
    data object NeedsSignIn : AuthGateState()
    data class SignedIn(val email: String?) : AuthGateState()
}

enum class AuthFormMode { SignIn, SignUp, Forgot }

enum class SignUpStep { Identifiers, Otp, Password }

enum class ForgotStep { Identifiers, Code }

data class SignInUiState(
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val otpCode: String = "",
    val mode: AuthFormMode = AuthFormMode.SignIn,
    val signUpStep: SignUpStep = SignUpStep.Identifiers,
    val forgotStep: ForgotStep = ForgotStep.Identifiers,
    val proofToken: String? = null,
    val stubHint: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

/**
 * GoTrue email/password + Edge phone login; Edge auth-otp signup (email and/or phone);
 * Edge password-reset. Live only — Fake mode bypasses this ViewModel in [AuthGate].
 */
class AuthSessionViewModel(
    private val supabase: SupabaseRpcClient,
) : ViewModel() {
    private val _gate = MutableStateFlow<AuthGateState>(AuthGateState.Checking)
    val gate: StateFlow<AuthGateState> = _gate.asStateFlow()

    private val _signIn = MutableStateFlow(SignInUiState())
    val signIn: StateFlow<SignInUiState> = _signIn.asStateFlow()

    init {
        viewModelScope.launch {
            supabase.sessionStatus.collect { status ->
                _gate.value = when (status) {
                    is SessionStatus.Initializing -> AuthGateState.Checking
                    is SessionStatus.Authenticated ->
                        AuthGateState.SignedIn(status.session.user?.email)
                    is SessionStatus.NotAuthenticated -> AuthGateState.NeedsSignIn
                    is SessionStatus.RefreshFailure -> AuthGateState.NeedsSignIn
                }
            }
        }
    }

    fun onEmailChange(v: String) = _signIn.update { it.copy(email = v, error = null, info = null) }
    fun onPhoneChange(v: String) = _signIn.update { it.copy(phone = v, error = null, info = null) }
    fun onPasswordChange(v: String) =
        _signIn.update { it.copy(password = v, error = null, info = null) }
    fun onOtpCodeChange(v: String) =
        _signIn.update { it.copy(otpCode = v.filter { c -> c.isDigit() }.take(6), error = null) }

    fun setMode(mode: AuthFormMode) = _signIn.update {
        it.copy(
            mode = mode,
            error = null,
            info = null,
            otpCode = "",
            proofToken = null,
            stubHint = null,
            password = if (mode == AuthFormMode.SignIn) it.password else "",
            signUpStep = SignUpStep.Identifiers,
            forgotStep = ForgotStep.Identifiers,
        )
    }

    fun signIn() {
        val s = _signIn.value
        val email = s.email.trim().takeIf { it.isNotBlank() }
        val phone = AuthEdge.normalizeE164(s.phone)
        val identifier = s.email.trim().ifBlank { s.phone.trim() }
        // Single-field convenience: user may put phone in the email box.
        val resolvedEmail: String?
        val resolvedPhone: String?
        when {
            email != null && email.contains("@") -> {
                resolvedEmail = email
                resolvedPhone = phone
            }
            AuthEdge.looksLikePhone(identifier) -> {
                resolvedEmail = null
                resolvedPhone = AuthEdge.normalizeE164(identifier) ?: phone
            }
            else -> {
                resolvedEmail = email
                resolvedPhone = phone
            }
        }
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                supabase.signInWithEmailOrPhone(
                    email = resolvedEmail,
                    phoneE164 = resolvedPhone,
                    password = s.password,
                )
                _signIn.update { it.copy(busy = false, password = "") }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Sign-in failed"))
                }
            }
        }
    }

    fun signInWithGoogleIdToken(idToken: String, rawNonce: String?) {
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                supabase.signInWithGoogleIdToken(idToken, rawNonce)
                _signIn.update { it.copy(busy = false, password = "") }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Google sign-in failed"))
                }
            }
        }
    }

    /** Primary CTA for SignUp / Forgot modes — advances the Edge OTP wizard. */
    fun primaryAction() {
        when (_signIn.value.mode) {
            AuthFormMode.SignIn -> signIn()
            AuthFormMode.SignUp -> when (_signIn.value.signUpStep) {
                SignUpStep.Identifiers -> requestSignupOtp()
                SignUpStep.Otp -> verifySignupOtp()
                SignUpStep.Password -> completeSignup()
            }
            AuthFormMode.Forgot -> when (_signIn.value.forgotStep) {
                ForgotStep.Identifiers -> requestResetOtp()
                ForgotStep.Code -> completeReset()
            }
        }
    }

    private fun requestSignupOtp() {
        val email = _signIn.value.email.trim().takeIf { it.isNotBlank() }
        val phone = AuthEdge.normalizeE164(_signIn.value.phone)
        if (email == null && phone == null) {
            _signIn.update { it.copy(error = "Enter email and/or phone") }
            return
        }
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null, stubHint = null) }
            try {
                val res = supabase.requestSignupOtp(email = email, phoneE164 = phone)
                _signIn.update {
                    it.copy(
                        busy = false,
                        signUpStep = SignUpStep.Otp,
                        stubHint = res.stubCode,
                        info = if (res.stub) {
                            "Local stub OTP — enter the code shown below."
                        } else {
                            "Code sent — check email and/or SMS."
                        },
                    )
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "OTP request failed"))
                }
            }
        }
    }

    private fun verifySignupOtp() {
        val email = _signIn.value.email.trim().takeIf { it.isNotBlank() }
        val phone = AuthEdge.normalizeE164(_signIn.value.phone)
        val code = _signIn.value.otpCode
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                val res = supabase.verifySignupOtp(email = email, phoneE164 = phone, code = code)
                _signIn.update {
                    it.copy(
                        busy = false,
                        proofToken = res.proofToken,
                        email = it.email.ifBlank { res.email.orEmpty() },
                        phone = it.phone.ifBlank { res.phoneE164.orEmpty() },
                        signUpStep = SignUpStep.Password,
                        password = "",
                        info = if (it.email.isBlank() && res.email.isNullOrBlank()) {
                            "Phone OTP verified — add an email + password to create your account."
                        } else {
                            "OTP verified — choose a password (min 8 characters)."
                        },
                    )
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "OTP verify failed"))
                }
            }
        }
    }

    private fun completeSignup() {
        val s = _signIn.value
        val proof = s.proofToken
        if (proof.isNullOrBlank()) {
            _signIn.update { it.copy(error = "Verify OTP before creating an account.") }
            return
        }
        if (s.email.isBlank()) {
            _signIn.update {
                it.copy(error = "Email is required to create a storefront account.")
            }
            return
        }
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                supabase.completeSignupWithOtp(
                    email = s.email,
                    password = s.password,
                    proofToken = proof,
                    phoneE164 = AuthEdge.normalizeE164(s.phone),
                )
                _signIn.update {
                    it.copy(busy = false, password = "", otpCode = "", proofToken = null)
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Sign-up failed"))
                }
            }
        }
    }

    private fun requestResetOtp() {
        val email = _signIn.value.email.trim().takeIf { it.isNotBlank() }
        val phone = AuthEdge.normalizeE164(_signIn.value.phone)
        if (email == null && phone == null) {
            _signIn.update { it.copy(error = "Enter email and/or phone to reset password") }
            return
        }
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null, stubHint = null) }
            try {
                val res = supabase.requestPasswordResetOtp(email = email, phoneE164 = phone)
                _signIn.update {
                    it.copy(
                        busy = false,
                        forgotStep = ForgotStep.Code,
                        stubHint = res.stubCode,
                        info = if (res.stub) {
                            "Local stub OTP — enter the code shown below."
                        } else {
                            "If an account exists, a reset code was sent."
                        },
                    )
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Reset request failed"))
                }
            }
        }
    }

    private fun completeReset() {
        val s = _signIn.value
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                supabase.completePasswordReset(
                    email = s.email.trim().takeIf { it.isNotBlank() },
                    phoneE164 = AuthEdge.normalizeE164(s.phone),
                    code = s.otpCode,
                    newPassword = s.password,
                )
                _signIn.update {
                    it.copy(
                        busy = false,
                        mode = AuthFormMode.SignIn,
                        forgotStep = ForgotStep.Identifiers,
                        otpCode = "",
                        password = "",
                        info = "Password updated — sign in with your new password.",
                    )
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Reset failed"))
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                supabase.signOut()
            } catch (e: Exception) {
                _signIn.update { it.copy(error = UserFacingErrors.from(e, "Sign-out failed")) }
            }
        }
    }

    companion object {
        fun factory(supabase: SupabaseRpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AuthSessionViewModel(supabase) as T
            }
    }
}
