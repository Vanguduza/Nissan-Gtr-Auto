package co.zw.nissangtr.pos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.pos.api.LivePosClient
import co.zw.nissangtr.pos.api.PosClient
import co.zw.nissangtr.pos.till.IdleLockController
import co.zw.nissangtr.pos.till.TillLayoutMode
import co.zw.nissangtr.pos.till.TillSession
import kotlinx.coroutines.launch

/**
 * Survives configuration changes (portrait ↔ landscape). Holds signed-in flag,
 * till session, and interaction clock used by idle lock.
 */
class PosShellViewModel(
    private val client: PosClient,
    private val liveClient: LivePosClient?,
    private val forceFake: Boolean,
    initialLayout: TillLayoutMode,
) : ViewModel() {
    val session: TillSession = TillSession(client, initialLayout)

    var signedIn by mutableStateOf(false)
        private set
    var staffName by mutableStateOf("Staff")
        private set
    var lastInteractionMs by mutableLongStateOf(System.currentTimeMillis())
        private set
    var handoffTried by mutableStateOf(false)
    var openFloatPeriodId by mutableStateOf<String?>(null)
    var ui by mutableStateOf(session.state)
        private set

    init {
        // Restore Live GoTrue session after Activity recreate when the
        // Application-scoped client still has tokens in memory.
        if (!forceFake && liveClient?.isSignedIn() == true) {
            staffName = displayNameFromEmail(liveClient.currentUserEmail()) ?: "Staff"
            signedIn = true
            lastInteractionMs = System.currentTimeMillis()
            refresh()
            viewModelScope.launch {
                session.ensureOpenCart()
                refresh()
            }
        }
    }

    fun refresh() {
        ui = session.state.copy(
            fake = session.state.fake.copy(
                session = session.state.fake.session.copy(staffName = staffName),
            ),
        )
    }

    fun markHandoffTried() {
        handoffTried = true
    }

    fun signIn(name: String) {
        staffName = name.ifBlank { "Staff" }
        signedIn = true
        lastInteractionMs = System.currentTimeMillis()
        viewModelScope.launch {
            session.ensureOpenCart()
            refresh()
        }
    }

    fun signOut() {
        signedIn = false
        lastInteractionMs = System.currentTimeMillis()
    }

    fun touch() {
        lastInteractionMs = System.currentTimeMillis()
    }

    fun shouldIdleLock(nowMs: Long, idleMs: Long): Boolean =
        IdleLockController.shouldLock(lastInteractionMs, nowMs, idleMs)

    fun setLayout(mode: TillLayoutMode) {
        session.setLayout(mode)
        refresh()
    }

    class Factory(
        private val client: PosClient,
        private val liveClient: LivePosClient?,
        private val forceFake: Boolean,
        private val initialLayout: TillLayoutMode,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PosShellViewModel(client, liveClient, forceFake, initialLayout) as T
        }
    }

    companion object {
        fun displayNameFromEmail(email: String?): String? {
            if (email.isNullOrBlank()) return null
            return email.substringBefore("@")
                .replace('.', ' ')
                .split(' ')
                .joinToString(" ") { part ->
                    part.replaceFirstChar { c -> c.uppercaseChar() }
                }
                .ifBlank { null }
        }
    }
}
