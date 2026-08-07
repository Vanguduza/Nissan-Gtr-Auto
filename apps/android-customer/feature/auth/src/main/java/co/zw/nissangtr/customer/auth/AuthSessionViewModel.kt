package co.zw.nissangtr.customer.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val mode: AuthFormMode = AuthFormMode.SignIn,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

enum class AuthFormMode { SignIn, SignUp }

/**
 * Observes GoTrue [SessionStatus] and drives email/password + Google ID-token sign-in / sign-out.
 * Live only — Fake mode bypasses this ViewModel in [AuthGate].
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
    fun onPasswordChange(v: String) = _signIn.update { it.copy(password = v, error = null, info = null) }
    fun setMode(mode: AuthFormMode) = _signIn.update {
        it.copy(mode = mode, error = null, info = null)
    }

    fun signIn() {
        val email = _signIn.value.email
        val password = _signIn.value.password
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                supabase.signInWithEmail(email, password)
                _signIn.update { it.copy(busy = false, password = "") }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Sign-in failed"))
                }
            }
        }
    }

    /**
     * Exchange a Google ID token for a GoTrue session, then ensure retail `customers` row.
     */
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

    fun signUp() {
        val email = _signIn.value.email
        val password = _signIn.value.password
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                supabase.signUpWithEmail(email, password)
                _signIn.update {
                    it.copy(
                        busy = false,
                        password = "",
                        mode = AuthFormMode.SignIn,
                        info = "Account created. Check email if confirmation is required, then sign in.",
                    )
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = UserFacingErrors.from(e, "Sign-up failed"))
                }
            }
        }
    }

    fun forgotPassword() {
        val email = _signIn.value.email
        if (email.isBlank()) {
            _signIn.update { it.copy(error = "Enter your email to reset password") }
            return
        }
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                supabase.resetPasswordForEmail(email)
                _signIn.update {
                    it.copy(
                        busy = false,
                        info = "Password reset email sent (if the account exists).",
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
