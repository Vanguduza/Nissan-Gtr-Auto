package co.zw.nissangtr.customer.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.AuthEdgeClient
import co.zw.nissangtr.customer.rpc.AuthEdgeSession
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

enum class AuthFormMode { SignIn, SignUp, ResetPassword }

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val mode: AuthFormMode = AuthFormMode.SignIn,
    val verificationPending: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

/**
 * Supabase Auth remains the identity/session authority. Password login, signup
 * verification and recovery enter through the hardened Auth Edge so project
 * identifier/IP/device throttles cannot be bypassed by this native client.
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
                    is SessionStatus.Authenticated -> AuthGateState.SignedIn(status.session.user?.email)
                    is SessionStatus.NotAuthenticated -> AuthGateState.NeedsSignIn
                    is SessionStatus.RefreshFailure -> AuthGateState.NeedsSignIn
                }
            }
        }
    }

    fun onEmailChange(v: String) = _signIn.update { it.copy(email = v, error = null, info = null) }
    fun onPasswordChange(v: String) = _signIn.update { it.copy(password = v, error = null, info = null) }
    fun onCodeChange(v: String) = _signIn.update {
        it.copy(code = v.filter(Char::isDigit).take(10), error = null, info = null)
    }

    fun setMode(mode: AuthFormMode) = _signIn.update {
        it.copy(
            mode = mode,
            code = "",
            verificationPending = false,
            password = if (mode == AuthFormMode.SignIn) it.password else "",
            error = null,
            info = null,
        )
    }

    private suspend fun adoptSession(session: AuthEdgeSession) {
        supabase.importAccessToken(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresIn = session.expiresIn,
        )
        supabase.ensureOwnCustomerIfNeeded()
    }

    fun signIn() {
        val email = _signIn.value.email.trim()
        val password = _signIn.value.password
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                val session = AuthEdgeClient.login(
                    client = supabase.client,
                    email = email,
                    password = password,
                )
                adoptSession(session)
                _signIn.update { it.copy(busy = false, password = "", code = "") }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Sign-in failed"))
                }
            }
        }
    }

    fun signUp() {
        val state = _signIn.value
        val email = state.email.trim()
        val password = state.password
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                if (!state.verificationPending) {
                    require(password.length >= 8) { "Password must be at least 8 characters" }
                    AuthEdgeClient.requestSignupOtp(
                        client = supabase.client,
                        email = email,
                        channel = "email",
                    )
                    _signIn.update {
                        it.copy(
                            busy = false,
                            verificationPending = true,
                            info = "Supabase Auth sent a verification code to your email.",
                        )
                    }
                    return@launch
                }

                require(state.code.length >= 6) { "Enter the verification code" }
                val verified = AuthEdgeClient.verifySignupOtp(
                    client = supabase.client,
                    email = email,
                    channel = "email",
                    code = state.code,
                )
                require(verified.emailVerified && verified.signupReady) {
                    "Email verification is incomplete"
                }
                val session = AuthEdgeClient.completeSignup(
                    client = supabase.client,
                    email = email,
                    password = password,
                )
                adoptSession(session)
                _signIn.update {
                    it.copy(
                        busy = false,
                        password = "",
                        code = "",
                        verificationPending = false,
                        mode = AuthFormMode.SignIn,
                        info = "Account created.",
                    )
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Sign-up failed"))
                }
            }
        }
    }

    fun resendSignupCode() {
        val email = _signIn.value.email.trim()
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                AuthEdgeClient.requestSignupOtp(supabase.client, email, channel = "email")
                _signIn.update { it.copy(busy = false, info = "A new verification code was requested.") }
            } catch (e: Exception) {
                _signIn.update { it.copy(busy = false, error = UserFacingErrors.from(e, "Resend failed")) }
            }
        }
    }

    fun forgotPassword() {
        val email = _signIn.value.email.trim()
        if (email.isBlank()) {
            _signIn.update { it.copy(error = "Enter your email to reset password") }
            return
        }
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                AuthEdgeClient.requestPasswordReset(supabase.client, email)
                _signIn.update {
                    it.copy(
                        busy = false,
                        mode = AuthFormMode.ResetPassword,
                        verificationPending = true,
                        password = "",
                        code = "",
                        info = "If the account exists, Supabase Auth sent a recovery code.",
                    )
                }
            } catch (e: Exception) {
                _signIn.update { it.copy(busy = false, error = UserFacingErrors.from(e, "Reset failed")) }
            }
        }
    }

    fun completePasswordReset() {
        val state = _signIn.value
        val email = state.email.trim()
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                require(state.code.length >= 6) { "Enter the recovery code" }
                require(state.password.length >= 8) { "New password must be at least 8 characters" }
                val session = AuthEdgeClient.verifyPasswordReset(
                    client = supabase.client,
                    email = email,
                    code = state.code,
                    newPassword = state.password,
                )
                adoptSession(session)
                _signIn.update {
                    it.copy(
                        busy = false,
                        code = "",
                        password = "",
                        verificationPending = false,
                        mode = AuthFormMode.SignIn,
                        info = "Password updated.",
                    )
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Password reset failed"))
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
