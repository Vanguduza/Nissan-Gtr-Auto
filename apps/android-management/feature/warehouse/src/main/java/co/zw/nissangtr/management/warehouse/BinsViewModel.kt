package co.zw.nissangtr.management.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.escpos.BluetoothPermissionStatus
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosReceiptLine
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.PickPathHint
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.WarehouseBinSummary
import co.zw.nissangtr.management.rpc.WarehouseRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BinsUiState(
    val warehouses: List<WarehouseRef> = emptyList(),
    val warehouseId: String = "",
    val bins: List<WarehouseBinSummary> = emptyList(),
    val code: String = "",
    val name: String = "",
    val pickPathSeq: String = "100",
    val aisle: String = "",
    val rack: String = "",
    val shelf: String = "",
    val assignStockItemId: String = "",
    val assignBinId: String = "",
    val pickItemIds: String = "",
    val pickHints: List<PickPathHint> = emptyList(),
    val printerMac: String = "",
    val printerConnected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Phase 16 bins + [RpcNames.GET_PICK_PATH_HINTS] + ESC/POS bin label (receipt lines).
 * Print fails gracefully when printer not paired/connected.
 */
class BinsViewModel(
    private val rpc: RpcClient,
    private val printer: EscPosPrinterBridge,
) : ViewModel() {
    private val _state = MutableStateFlow(BinsUiState())
    val state: StateFlow<BinsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { rpc.listWarehouses() }
                .onSuccess { list ->
                    val first = list.firstOrNull()
                    _state.update {
                        it.copy(
                            warehouses = list,
                            warehouseId = first?.id
                                ?: if (rpc is FakeRpcClient) FakeRpcClient.FAKE_WAREHOUSE_ID else "",
                        )
                    }
                    if (_state.value.warehouseId.isNotBlank()) refreshBins()
                }
        }
    }

    fun onWarehouseIdChange(v: String) =
        _state.update { it.copy(warehouseId = v, error = null) }

    fun selectWarehouse(wh: WarehouseRef) {
        _state.update { it.copy(warehouseId = wh.id, error = null) }
        refreshBins()
    }

    fun onCodeChange(v: String) = _state.update { it.copy(code = v) }
    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onPickPathSeqChange(v: String) = _state.update { it.copy(pickPathSeq = v) }
    fun onAisleChange(v: String) = _state.update { it.copy(aisle = v) }
    fun onRackChange(v: String) = _state.update { it.copy(rack = v) }
    fun onShelfChange(v: String) = _state.update { it.copy(shelf = v) }
    fun onAssignStockItemIdChange(v: String) = _state.update { it.copy(assignStockItemId = v) }
    fun onAssignBinIdChange(v: String) = _state.update { it.copy(assignBinId = v) }
    fun onPickItemIdsChange(v: String) = _state.update { it.copy(pickItemIds = v) }
    fun onPrinterMacChange(v: String) = _state.update { it.copy(printerMac = v) }

    fun refreshBins() {
        val wh = _state.value.warehouseId.trim()
        if (wh.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val bins = rpc.listWarehouseBins(wh)
                _state.update { it.copy(busy = false, bins = bins, message = "${bins.size} bin(s)") }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list bins failed") }
            }
        }
    }

    fun createBin() {
        val s = _state.value
        val seq = s.pickPathSeq.toIntOrNull() ?: 100
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createWarehouseBin(
                    warehouseId = s.warehouseId.trim(),
                    code = s.code.trim(),
                    name = s.name.trim(),
                    pickPathSeq = seq,
                    aisle = s.aisle.trim().ifBlank { null },
                    rack = s.rack.trim().ifBlank { null },
                    shelf = s.shelf.trim().ifBlank { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        code = "",
                        name = "",
                        message = "${RpcNames.CREATE_WAREHOUSE_BIN} → $id",
                    )
                }
                refreshBins()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "create bin failed") }
            }
        }
    }

    fun deactivateBin(binId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.deactivateWarehouseBin(binId)
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.DEACTIVATE_WAREHOUSE_BIN} → $binId")
                }
                refreshBins()
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "deactivate failed") }
            }
        }
    }

    fun assignPreferredBin() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.setStockLevelBin(
                    stockItemId = s.assignStockItemId.trim(),
                    warehouseId = s.warehouseId.trim(),
                    binId = s.assignBinId.trim().ifBlank { null },
                )
                _state.update {
                    it.copy(busy = false, message = "${RpcNames.SET_STOCK_LEVEL_BIN} → $id")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "assign bin failed") }
            }
        }
    }

    fun loadPickPathHints() {
        val s = _state.value
        val ids = s.pickItemIds.split(',', ' ', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val hints = rpc.getPickPathHints(
                    warehouseId = s.warehouseId.trim(),
                    stockItemIds = ids.ifEmpty { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        pickHints = hints,
                        message = "${RpcNames.GET_PICK_PATH_HINTS} → ${hints.size} hint(s)",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "pick-path failed") }
            }
        }
    }

    fun connectPrinter() {
        val mac = _state.value.printerMac.trim()
        if (mac.isEmpty()) {
            _state.update { it.copy(error = "Printer Bluetooth MAC required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                ensureBluetooth()
                printer.configurePrinterAddress(mac)
                printer.connect()
                _state.update {
                    it.copy(busy = false, printerConnected = true, message = "Printer connected")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        printerConnected = false,
                        error = e.message ?: "printer connect failed — pair in system Bluetooth first",
                    )
                }
            }
        }
    }

    fun printBinLabel(bin: WarehouseBinSummary) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                if (!printer.isConnected()) {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "Printer not connected — set MAC + Connect, or skip label",
                        )
                    }
                    return@launch
                }
                printer.printReceiptLines(
                    listOf(
                        EscPosReceiptLine("GTR BIN LABEL", emphasis = true),
                        EscPosReceiptLine(bin.code, emphasis = true),
                        EscPosReceiptLine(bin.name),
                        EscPosReceiptLine(
                            listOfNotNull(
                                bin.aisle?.let { "Aisle $it" },
                                bin.rack?.let { "Rack $it" },
                                bin.shelf?.let { "Shelf $it" },
                            ).joinToString(" · ").ifBlank { "seq ${bin.pickPathSeq}" },
                        ),
                        EscPosReceiptLine("Pick seq: ${bin.pickPathSeq}"),
                    ),
                )
                _state.update { it.copy(busy = false, message = "Bin label printed · ${bin.code}") }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = "Print skipped: ${e.message ?: "not paired / offline"}",
                    )
                }
            }
        }
    }

    private suspend fun ensureBluetooth() {
        var status = printer.getBluetoothPermissionStatus()
        if (status != BluetoothPermissionStatus.GRANTED) {
            status = printer.requestBluetoothPermission()
        }
        require(status == BluetoothPermissionStatus.GRANTED) {
            "Bluetooth permission required for ESC/POS"
        }
    }

    companion object {
        fun factory(rpc: RpcClient, printer: EscPosPrinterBridge) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BinsViewModel(rpc, printer) as T
            }
    }
}
