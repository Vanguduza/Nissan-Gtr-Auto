package co.zw.nissangtr.management.hr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.AttendanceEventType
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClockAttendanceUiState(
    val employeeId: String = "",
    val notes: String = "",
    val busy: Boolean = false,
    val lastEventId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class ClockAttendanceViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(ClockAttendanceUiState())
    val state: StateFlow<ClockAttendanceUiState> = _state.asStateFlow()

    fun onEmployeeIdChange(value: String) {
        _state.update { it.copy(employeeId = value, error = null) }
    }

    fun onNotesChange(value: String) {
        _state.update { it.copy(notes = value) }
    }

    fun clock(eventType: AttendanceEventType) {
        val employeeId = _state.value.employeeId.trim()
        if (employeeId.isEmpty()) {
            _state.update { it.copy(error = "Employee UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val eventId = rpc.clockAttendance(
                    employeeId = employeeId,
                    eventType = eventType,
                    notes = _state.value.notes.ifBlank { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastEventId = eventId,
                        message = "${eventType.rpcValue} via ${RpcNames.CLOCK_ATTENDANCE} → $eventId",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "clock failed")
                }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ClockAttendanceViewModel(rpc) as T
            }
    }
}
