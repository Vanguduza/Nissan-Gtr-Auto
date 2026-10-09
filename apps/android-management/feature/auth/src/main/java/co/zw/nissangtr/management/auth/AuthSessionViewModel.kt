package co.zw.nissangtr.management.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
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

enum class StaffAuthMode { SignIn, ResetPassword }

data class SignInUiState(
    val identifier: String = "",
    val password: String = "",
    val code: String = "",
    val mode: StaffAuthMode = StaffAuthMode.SignIn,
    val resetRequested: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

/**
 * Staff authentication uses the hardened Auth Edge for password grants and
 * recovery. Supabase Auth remains the identity/session authority; employee # /
 * phone are resolved to the canonical Auth email before the Edge password grant.
 */
class AuthSessionViewModel(
    private val supabase: SupabaseRpcClient,
) : ViewModel() {
    private val _gate = MutableStateFlow<AuthGateState>(AuthGateState.Checking)
    val gate: StateFlow<AuthGateState> = _gate.asStateFlow()

    private val _signIn = MutableStateFlow(SignInUiState())
    val signIn: StateFlow<SignInUiState> = _signIn.asStateFlow()
    private var resetEmail: String? = null

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

    fun onIdentifierChange(v: String) = _signIn.update { it.copy(identifier = v, error = null, info = null) }
    fun onEmailChange(v: String) = onIdentifierChange(v)
    fun onPasswordChange(v: String) = _signIn.update { it.copy(password = v, error = null, info = null) }
    fun onCodeChange(v: String) = _signIn.update {
        it.copy(code = v.filter(Char::isDigit).take(10), error = null, info = null)
    }

    fun showSignIn() {
        resetEmail = null
        _signIn.update {
            it.copy(mode = StaffAuthMode.SignIn, resetRequested = false, code = "", password = "", error = null, info = null)
        }
    }

    fun signIn() {
        val identifier = _signIn.value.identifier.trim()
        val password = _signIn.value.password
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                val email = supabase.resolveStaffLoginEmail(identifier)
                supabase.signInWithEmail(email, password)
                _signIn.update { it.copy(busy = false, password = "", code = "") }
            } catch (_: Exception) {
                _signIn.update { it.copy(busy = false, error = "Sign-in failed") }
            }
        }
    }

    /**
     * Non-enumerating reset request. Invalid employee/email/phone identifiers get
     * the same UI response as valid ones; only valid resolved users receive mail.
     */
    fun requestPasswordReset() {
        val identifier = _signIn.value.identifier.trim()
        if (identifier.isBlank()) {
            _signIn.update { it.copy(error = "Enter your employee #, email or phone") }
            return
        }
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            val resolved = runCatching { supabase.resolveStaffLoginEmail(identifier) }.getOrNull()
            resetEmail = resolved
            if (resolved != null) {
                runCatching { supabase.requestPasswordResetForEmail(resolved) }
            }
            _signIn.update {
                it.copy(
                    busy = false,
                    mode = StaffAuthMode.ResetPassword,
                    resetRequested = true,
                    code = "",
                    password = "",
                    info = "If the staff account exists, Supabase Auth sent a recovery code.",
                )
            }
        }
    }

    fun completePasswordReset() {
        val state = _signIn.value
        val email = resetEmail
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null, info = null) }
            try {
                require(email != null) { "Recovery request is not valid" }
                require(state.code.length >= 6) { "Enter the recovery code" }
                require(state.password.length >= 8) { "New password must be at least 8 characters" }
                supabase.completePasswordResetForEmail(email, state.code, state.password)
                _signIn.update {
                    it.copy(
                        busy = false,
                        code = "",
                        password = "",
                        resetRequested = false,
                        mode = StaffAuthMode.SignIn,
                        info = "Password updated.",
                    )
                }
                resetEmail = null
            } catch (_: Exception) {
                _signIn.update { it.copy(busy = false, error = "Recovery code is invalid, expired, or the reset request is unavailable") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try { supabase.signOut() }
            catch (e: Exception) { _signIn.update { it.copy(error = e.message ?: "Sign-out failed") } }
        }
    }

    companion object {
        fun factory(supabase: SupabaseRpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthSessionViewModel(supabase) as T
            }
    }
}
