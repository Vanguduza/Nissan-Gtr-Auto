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

data class SignInUiState(
    /** Emp# or email or phone — resolved before GoTrue password. */
    val identifier: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Observes GoTrue [SessionStatus] and drives staff identifier + password sign-in.
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

    fun onIdentifierChange(v: String) = _signIn.update { it.copy(identifier = v, error = null) }

    /** @deprecated Prefer [onIdentifierChange] — kept for any email-only call sites. */
    fun onEmailChange(v: String) = onIdentifierChange(v)

    fun onPasswordChange(v: String) = _signIn.update { it.copy(password = v, error = null) }

    fun signIn() {
        val identifier = _signIn.value.identifier
        val password = _signIn.value.password
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null) }
            try {
                val locked = runCatching { supabase.staffLoginIsLocked(identifier) }
                    .getOrDefault(false)
                if (locked) {
                    _signIn.update {
                        it.copy(
                            busy = false,
                            error = "Too many attempts — try again later",
                        )
                    }
                    return@launch
                }
                val email = supabase.resolveStaffLoginEmail(identifier)
                try {
                    supabase.signInWithEmail(email, password)
                    runCatching { supabase.recordStaffLoginAttempt(identifier, true) }
                    _signIn.update { it.copy(busy = false, password = "") }
                } catch (e: Exception) {
                    runCatching { supabase.recordStaffLoginAttempt(identifier, false) }
                    _signIn.update {
                        it.copy(
                            busy = false,
                            // Non-enumerating UX — do not leak whether emp#/email exists.
                            error = "Sign-in failed",
                        )
                    }
                }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(
                        busy = false,
                        error = "Sign-in failed",
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                supabase.signOut()
            } catch (e: Exception) {
                _signIn.update { it.copy(error = e.message ?: "Sign-out failed") }
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
