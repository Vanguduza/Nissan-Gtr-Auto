package co.zw.nissangtr.customer.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.ChatMessage
import co.zw.nissangtr.customer.rpc.ChatThread
import co.zw.nissangtr.customer.rpc.ChatThreadKind
import co.zw.nissangtr.customer.rpc.ChatThreadStatus
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.StartChatThreadInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Poll interval for open thread messages.
 *
 * Gap: supabase-kt client installs Auth + Postgrest only — no realtime-kt —
 * so we poll instead of postgres_changes (web uses Realtime INSERT).
 */
private const val POLL_MS = 3_000L

data class ChatUiState(
    val threads: List<ChatThread> = emptyList(),
    val selectedId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val unread: Int = 0,
    val kind: ChatThreadKind = ChatThreadKind.SUPPORT,
    val subject: String = "",
    val firstBody: String = "",
    val draft: String = "",
    val busy: Boolean = false,
    val sendBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** True while poll loop is active for the selected thread. */
    val polling: Boolean = false,
) {
    val selected: ChatThread?
        get() = threads.find { it.id == selectedId }

    val threadClosed: Boolean
        get() = selected?.status == ChatThreadStatus.CLOSED
}

class ChatViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refreshThreads()
    }

    fun onKindChange(kind: ChatThreadKind) {
        _state.update { it.copy(kind = kind) }
    }

    fun onSubjectChange(value: String) {
        _state.update { it.copy(subject = value.take(200)) }
    }

    fun onFirstBodyChange(value: String) {
        _state.update { it.copy(firstBody = value.take(4000)) }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value.take(4000)) }
    }

    fun refreshThreads() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listChatThreads()
                val unread = runCatching { rpc.chatUnreadCount(null) }.getOrDefault(0)
                _state.update {
                    it.copy(busy = false, threads = list, unread = unread)
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list threads failed") }
            }
        }
    }

    fun openThread(threadId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedId = threadId,
                    busy = true,
                    error = null,
                    message = null,
                )
            }
            try {
                val msgs = rpc.listChatMessages(threadId)
                runCatching { rpc.markChatThreadRead(threadId) }
                val unread = runCatching { rpc.chatUnreadCount(null) }.getOrDefault(0)
                _state.update {
                    it.copy(
                        busy = false,
                        messages = msgs,
                        unread = unread,
                    )
                }
                startPolling(threadId)
            } catch (e: Exception) {
                stopPolling()
                _state.update { it.copy(busy = false, error = e.message ?: "open thread failed") }
            }
        }
    }

    fun clearSelection() {
        stopPolling()
        _state.update {
            it.copy(selectedId = null, messages = emptyList(), polling = false)
        }
    }

    fun startThread() {
        viewModelScope.launch {
            val s = _state.value
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.startChatThread(
                    StartChatThreadInput(
                        kind = s.kind,
                        subject = s.subject.trim().ifEmpty { null },
                        body = s.firstBody.trim().ifEmpty { null },
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        firstBody = "",
                        message = "Started thread $id",
                    )
                }
                refreshThreads()
                openThread(id)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "start failed") }
            }
        }
    }

    fun sendMessage() {
        val threadId = _state.value.selectedId ?: return
        val body = _state.value.draft.trim()
        if (body.isEmpty() || _state.value.threadClosed) return
        viewModelScope.launch {
            _state.update { it.copy(sendBusy = true, error = null) }
            try {
                rpc.postChatMessage(threadId, body)
                val msgs = rpc.listChatMessages(threadId)
                _state.update {
                    it.copy(sendBusy = false, draft = "", messages = msgs)
                }
                refreshThreads()
            } catch (e: Exception) {
                _state.update { it.copy(sendBusy = false, error = e.message ?: "send failed") }
            }
        }
    }

    private fun startPolling(threadId: String) {
        stopPolling()
        pollJob = viewModelScope.launch {
            _state.update { it.copy(polling = true) }
            while (isActive) {
                delay(POLL_MS)
                if (_state.value.selectedId != threadId) break
                try {
                    val msgs = rpc.listChatMessages(threadId)
                    val prev = _state.value.messages
                    if (msgs.size != prev.size || msgs.lastOrNull()?.id != prev.lastOrNull()?.id) {
                        runCatching { rpc.markChatThreadRead(threadId) }
                        val unread = runCatching { rpc.chatUnreadCount(null) }.getOrDefault(0)
                        _state.update { it.copy(messages = msgs, unread = unread) }
                        val list = rpc.listChatThreads()
                        _state.update { it.copy(threads = list) }
                    }
                } catch (_: Exception) {
                    // Keep last good state; next tick retries.
                }
            }
            _state.update { it.copy(polling = false) }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChatViewModel(rpc) as T
            }
    }
}
