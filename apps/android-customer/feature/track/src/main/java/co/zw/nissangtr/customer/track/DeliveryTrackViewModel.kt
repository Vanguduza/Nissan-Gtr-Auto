package co.zw.nissangtr.customer.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.DeliveryTrackPoint
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Poll interval for last-point refresh (mirrors web customer track panel).
 * No Realtime trail subscription — single-point RPC only.
 */
private const val POLL_MS = 15_000L

data class DeliveryTrackUiState(
    val tokenDraft: String = "",
    val jobIdDraft: String = "",
    /** Active query identity once tracking has started. */
    val activeToken: String? = null,
    val activeJobId: String? = null,
    val point: DeliveryTrackPoint? = null,
    val busy: Boolean = false,
    val polling: Boolean = false,
    val emptyHint: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val tracking: Boolean
        get() = activeToken != null || activeJobId != null
}

class DeliveryTrackViewModel(
    private val rpc: RpcClient,
    initialToken: String? = null,
    initialJobId: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        DeliveryTrackUiState(
            tokenDraft = initialToken.orEmpty(),
            jobIdDraft = initialJobId.orEmpty(),
        ),
    )
    val state: StateFlow<DeliveryTrackUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        val tok = initialToken?.trim()?.takeIf { it.isNotEmpty() }
        val job = initialJobId?.trim()?.takeIf { it.isNotEmpty() }
        if (tok != null || job != null) {
            startTracking(token = tok, jobId = job)
        }
    }

    fun onTokenChange(value: String) {
        _state.update { it.copy(tokenDraft = value.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(128)) }
    }

    fun onJobIdChange(value: String) {
        _state.update { it.copy(jobIdDraft = value.filter { c -> c.isLetterOrDigit() || c == '-' }.take(36)) }
    }

    fun startFromDrafts() {
        val tok = _state.value.tokenDraft.trim().takeIf { it.isNotEmpty() }
        val job = _state.value.jobIdDraft.trim().takeIf { it.isNotEmpty() }
        startTracking(token = tok, jobId = job)
    }

    fun startTracking(token: String?, jobId: String?) {
        val tok = token?.trim()?.takeIf { it.isNotEmpty() }
        val job = jobId?.trim()?.takeIf { it.isNotEmpty() }
        if (tok == null && job == null) {
            _state.update { it.copy(error = "Enter a share token or delivery job id") }
            return
        }
        if (tok != null && tok.length < 8) {
            _state.update { it.copy(error = "Invalid track token") }
            return
        }
        stopPolling()
        _state.update {
            it.copy(
                activeToken = tok,
                activeJobId = job,
                point = null,
                emptyHint = null,
                error = null,
                message = null,
            )
        }
        refreshOnce()
        startPolling()
    }

    fun refreshOnce() {
        val tok = _state.value.activeToken
        val job = _state.value.activeJobId
        if (tok == null && job == null) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, emptyHint = null) }
            try {
                val point = rpc.getDeliveryTrackPoint(deliveryJobId = job, token = tok)
                _state.update {
                    if (point == null) {
                        it.copy(
                            busy = false,
                            point = null,
                            emptyHint =
                                "No live location — link inactive/expired, or delivery is not out for delivery.",
                            message = "${RpcNames.GET_DELIVERY_TRACK_POINT} → empty",
                        )
                    } else {
                        it.copy(
                            busy = false,
                            point = point,
                            emptyHint = null,
                            message = "${RpcNames.GET_DELIVERY_TRACK_POINT} → last point",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "track failed")
                }
            }
        }
    }

    fun stopTracking() {
        stopPolling()
        _state.update {
            it.copy(
                activeToken = null,
                activeJobId = null,
                point = null,
                polling = false,
                emptyHint = null,
                message = "Tracking stopped",
            )
        }
    }

    private fun startPolling() {
        stopPolling()
        pollJob = viewModelScope.launch {
            _state.update { it.copy(polling = true) }
            while (isActive) {
                delay(POLL_MS)
                if (!isActive) break
                val tok = _state.value.activeToken
                val job = _state.value.activeJobId
                if (tok == null && job == null) break
                try {
                    val point = rpc.getDeliveryTrackPoint(deliveryJobId = job, token = tok)
                    _state.update {
                        it.copy(
                            point = point,
                            emptyHint = if (point == null) {
                                "No live location — link inactive/expired, or delivery is not out for delivery."
                            } else {
                                null
                            },
                            error = null,
                        )
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message ?: "poll failed") }
                }
            }
            _state.update { it.copy(polling = false) }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _state.update { it.copy(polling = false) }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        fun factory(
            rpc: RpcClient,
            initialToken: String? = null,
            initialJobId: String? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DeliveryTrackViewModel(rpc, initialToken, initialJobId) as T
            }
    }
}
