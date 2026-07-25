package co.zw.nissangtr.management.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.ChatMessageSummary
import co.zw.nissangtr.management.rpc.ChatThreadSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.StaffChatFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val filter: StaffChatFilter = StaffChatFilter.OPEN,
    val threads: List<ChatThreadSummary> = emptyList(),
    val selectedId: String? = null,
    val messages: List<ChatMessageSummary> = emptyList(),
    val draft: String = "",
    val unread: Int = 0,
    val currentUserId: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val sendBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Realtime not wired in supabase-kt client yet — poll refresh. */
    val pollNote: String = "Polling ~5s (Realtime not enabled in this client)",
)

/**
 * Staff inbox: open / mine / closed filters, claim, reply, close.
 * Prefer RPCs for mutations; list via PostgREST + RLS.
 */
class ChatViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(currentUserId = rpc.currentUserId()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refreshThreads()
        startPolling()
    }

    fun onFilterChange(filter: StaffChatFilter) {
        _state.update {
            it.copy(
                filter = filter,
                selectedId = null,
                messages = emptyList(),
                error = null,
            )
        }
        refreshThreads()
    }

    fun onDraftChange(v: String) =
        _state.update { it.copy(draft = v, error = null) }

    fun selectThread(threadId: String) {
        viewModelScope.launch {
            _state.update { it.copy(selectedId = threadId, error = null, busy = true) }
            try {
                val msgs = rpc.listChatMessages(threadId)
                runCatching { rpc.markChatThreadRead(threadId) }
                val unread = runCatching { rpc.chatUnreadCount() }.getOrDefault(0)
                _state.update {
                    it.copy(
                        busy = false,
                        messages = msgs,
                        unread = unread,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "open thread failed")
                }
            }
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedId = null, messages = emptyList(), draft = "") }
    }

    fun refreshThreads() {
        viewModelScope.launch {
            val filter = _state.value.filter
            _state.update { it.copy(loading = true, error = null) }
            try {
                val threads = rpc.listStaffChatThreads(filter)
                val unread = runCatching { rpc.chatUnreadCount() }.getOrDefault(0)
                _state.update {
                    it.copy(
                        loading = false,
                        threads = threads,
                        unread = unread,
                        currentUserId = rpc.currentUserId(),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "refresh failed")
                }
            }
        }
    }

    fun claim() {
        val threadId = _state.value.selectedId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.claimChatThread(threadId)
                _state.update {
                    it.copy(
                        busy = false,
                        filter = StaffChatFilter.MINE,
                        message = "Claimed",
                    )
                }
                refreshThreads()
                selectThread(threadId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "claim failed")
                }
            }
        }
    }

    fun close() {
        val threadId = _state.value.selectedId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.closeChatThread(threadId)
                _state.update { it.copy(busy = false, message = "Closed") }
                refreshThreads()
                selectThread(threadId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "close failed")
                }
            }
        }
    }

    fun send() {
        val threadId = _state.value.selectedId ?: return
        val body = _state.value.draft.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(sendBusy = true, error = null) }
            try {
                rpc.postChatMessage(threadId, body)
                val msgs = rpc.listChatMessages(threadId)
                _state.update {
                    it.copy(sendBusy = false, draft = "", messages = msgs, message = "Sent")
                }
                refreshThreads()
            } catch (e: Exception) {
                _state.update {
                    it.copy(sendBusy = false, error = e.message ?: "send failed")
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                val selected = _state.value.selectedId
                try {
                    val threads = rpc.listStaffChatThreads(_state.value.filter)
                    val unread = runCatching { rpc.chatUnreadCount() }.getOrDefault(0)
                    val messages = if (selected != null) {
                        rpc.listChatMessages(selected)
                    } else {
                        _state.value.messages
                    }
                    if (selected != null) {
                        runCatching { rpc.markChatThreadRead(selected) }
                    }
                    _state.update {
                        it.copy(
                            threads = threads,
                            unread = unread,
                            messages = messages,
                            currentUserId = rpc.currentUserId(),
                        )
                    }
                } catch (_: Exception) {
                    // Keep last good state; surface next user action error.
                }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val POLL_MS = 5_000L

        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(rpc) as T
            }
    }
}
