package co.zw.nissangtr.management.hr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.management.rpc.PayrollDeductionSummary
import co.zw.nissangtr.management.rpc.PayrollLineSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GrossPayrollUiState(
    val employeeId: String = "",
    val periodStart: String = LocalDate.now().withDayOfMonth(1).toString(),
    val periodEnd: String = LocalDate.now().toString(),
    val hours: Double? = null,
    val lines: List<PayrollLineSummary> = emptyList(),
    val selectedLineId: String = "",
    val deductions: List<PayrollDeductionSummary> = emptyList(),
    val deductionLabel: String = "",
    val deductionAmount: String = "",
    val bootLoading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Period hours + open payroll lines + manual deductions.
 * Net = gross − manual lines only — no PAYE / NSSA / tax brackets.
 */
class GrossPayrollViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(GrossPayrollUiState())
    val state: StateFlow<GrossPayrollUiState> = _state.asStateFlow()

    init {
        refreshLines()
    }

    fun onEmployeeIdChange(value: String) {
        _state.update { it.copy(employeeId = value, error = null, hours = null) }
    }

    fun onPeriodStartChange(value: String) {
        _state.update { it.copy(periodStart = value, hours = null) }
    }

    fun onPeriodEndChange(value: String) {
        _state.update { it.copy(periodEnd = value, hours = null) }
    }

    fun onDeductionLabelChange(value: String) {
        _state.update { it.copy(deductionLabel = value) }
    }

    fun onDeductionAmountChange(value: String) {
        _state.update { it.copy(deductionAmount = value) }
    }

    fun selectLine(lineId: String) {
        if (lineId == _state.value.selectedLineId) return
        _state.update { it.copy(selectedLineId = lineId, deductions = emptyList(), error = null) }
        loadDeductions(lineId)
    }

    fun lookupHours() {
        val employeeId = _state.value.employeeId.trim()
        if (employeeId.isEmpty()) {
            _state.update { it.copy(error = "Employee UUID required") }
            return
        }
        val start = toPeriodStartIso(_state.value.periodStart)
        val end = toPeriodEndIso(_state.value.periodEnd)
        if (start == null || end == null) {
            _state.update { it.copy(error = "Use YYYY-MM-DD for period dates") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val hours = rpc.attendanceHoursInPeriod(employeeId, start, end)
                _state.update {
                    it.copy(
                        busy = false,
                        hours = hours,
                        message = "${RpcNames.ATTENDANCE_HOURS_IN_PERIOD} → ${"%.2f".format(hours)} h",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "hours lookup failed")
                }
            }
        }
    }

    fun refreshLines() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, bootLoading = true, error = null) }
            try {
                val lines = rpc.listOpenPayrollLines()
                val selected = _state.value.selectedLineId
                    .takeIf { id -> lines.any { it.id == id } }
                    ?: lines.firstOrNull()?.id.orEmpty()
                _state.update {
                    it.copy(
                        busy = false,
                        bootLoading = false,
                        lines = lines,
                        selectedLineId = selected,
                        employeeId = it.employeeId.ifBlank {
                            lines.firstOrNull()?.employeeId.orEmpty()
                        },
                    )
                }
                if (selected.isNotBlank()) loadDeductions(selected)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        bootLoading = false,
                        error = e.message ?: "payroll lines failed",
                    )
                }
            }
        }
    }

    fun addDeduction() {
        val lineId = _state.value.selectedLineId
        val label = _state.value.deductionLabel.trim()
        val amount = _state.value.deductionAmount.toDoubleOrNull()
        if (lineId.isBlank()) {
            _state.update { it.copy(error = "Select a payroll line") }
            return
        }
        if (label.isEmpty()) {
            _state.update { it.copy(error = "Deduction label required") }
            return
        }
        if (amount == null || amount <= 0) {
            _state.update { it.copy(error = "Amount must be > 0") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.addPayrollDeduction(lineId, label, amount)
                _state.update {
                    it.copy(
                        busy = false,
                        deductionAmount = "",
                        message = "${RpcNames.ADD_PAYROLL_DEDUCTION} → $id (manual only)",
                    )
                }
                refreshLines()
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "add deduction failed")
                }
            }
        }
    }

    private fun loadDeductions(lineId: String) {
        viewModelScope.launch {
            try {
                val rows = rpc.listPayrollDeductions(lineId)
                _state.update { it.copy(deductions = rows) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.message ?: "deductions load failed", deductions = emptyList())
                }
            }
        }
    }

    companion object {
        private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

        fun toPeriodStartIso(dateInput: String): String? = runCatching {
            LocalDate.parse(dateInput.trim(), DATE)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toString()
        }.getOrNull()

        fun toPeriodEndIso(dateInput: String): String? = runCatching {
            LocalDate.parse(dateInput.trim(), DATE)
                .plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .minusNanos(1)
                .toInstant()
                .toString()
        }.getOrNull()

        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GrossPayrollViewModel(rpc) as T
            }
    }
}
