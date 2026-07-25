package co.zw.nissangtr.delivery.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.delivery.rpc.DriverStaffRoles
import co.zw.nissangtr.delivery.rpc.SupabaseRpcClient
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
    data class WrongRole(val roles: List<String>) : AuthGateState()
}

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Observes GoTrue [SessionStatus] and enforces staff role `driver` (or admin for QA).
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
                when (status) {
                    is SessionStatus.Initializing ->
                        _gate.value = AuthGateState.Checking
                    is SessionStatus.NotAuthenticated,
                    is SessionStatus.RefreshFailure,
                    -> _gate.value = AuthGateState.NeedsSignIn
                    is SessionStatus.Authenticated -> {
                        _gate.value = AuthGateState.Checking
                        val roles = runCatching { supabase.listMyStaffRoles() }
                            .getOrDefault(emptyList())
                        if (DriverStaffRoles.allows(roles)) {
                            _gate.value = AuthGateState.SignedIn(status.session.user?.email)
                        } else {
                            _gate.value = AuthGateState.WrongRole(roles)
                        }
                    }
                }
            }
        }
    }

    fun onEmailChange(v: String) = _signIn.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _signIn.update { it.copy(password = v, error = null) }

    fun signIn() {
        val email = _signIn.value.email
        val password = _signIn.value.password
        viewModelScope.launch {
            _signIn.update { it.copy(busy = true, error = null) }
            try {
                supabase.signInWithEmail(email, password)
                _signIn.update { it.copy(busy = false, password = "") }
            } catch (e: Exception) {
                _signIn.update {
                    it.copy(busy = false, error = e.message ?: "Sign-in failed")
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
