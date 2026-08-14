package co.zw.nissangtr.management.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.qr.CameraPermissionStatus
import co.zw.nissangtr.bridges.qr.InventoryQrValuation
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.bridges.qr.parseInventoryQrPayload
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.ReceiptLineInput
import co.zw.nissangtr.management.rpc.ReconciliationLineInput
import co.zw.nissangtr.management.rpc.ReconciliationScope
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.TransferLineInput
import co.zw.nissangtr.management.rpc.ValuationMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarehouseUiState(
    // Receive
    val receiveWarehouseId: String = "",
    val receiveNotes: String = "",
    val receiveStockItemId: String = "",
    val receiveUomId: String = "",
    val receiveQty: String = "1",
    val receiveUnitCost: String = "0",
    val receiveCurrency: CurrencyCode = CurrencyCode.USD,
    val receiveValuation: ValuationMethod = ValuationMethod.FIFO,
    val lastQrPayload: String? = null,
    // Transfer
    val fromWarehouseId: String = "",
    val toWarehouseId: String = "",
    val transferNotes: String = "",
    val transferStockItemId: String = "",
    val transferUomId: String = "",
    val transferQty: String = "1",
    val transferEntryId: String = "",
    // Cycle count
    val reconWarehouseId: String = "",
    val reconScope: ReconciliationScope = ReconciliationScope.PARTIAL,
    val reconCurrency: CurrencyCode = CurrencyCode.USD,
    val reconExchangeRate: String = "1",
    val reconItemIds: String = "",
    val reconNotes: String = "",
    val reconciliationId: String = "",
    val reconLineStockItemId: String = "",
    val reconCountedQty: String = "0",
    val cancelNotes: String = "",
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Warehouse scaffold: receive, dual-auth transfer, cycle-count draft/submit/approve/cancel.
 * Explicit USD|ZIG on receipt lines and recon drafts.
 * Bridge-First QR fills stock item / UOM from OEM — no browser QR.
 */
class WarehouseViewModel(
    private val rpc: RpcClient,
    private val qr: QrScannerBridge,
) : ViewModel() {
    private val _state = MutableStateFlow(WarehouseUiState())
    val state: StateFlow<WarehouseUiState> = _state.asStateFlow()

    fun onReceiveWarehouseIdChange(v: String) =
        _state.update { it.copy(receiveWarehouseId = v, error = null) }

    fun onReceiveNotesChange(v: String) =
        _state.update { it.copy(receiveNotes = v) }

    fun onReceiveStockItemIdChange(v: String) =
        _state.update { it.copy(receiveStockItemId = v, error = null) }

    fun onReceiveUomIdChange(v: String) =
        _state.update { it.copy(receiveUomId = v) }

    fun onReceiveQtyChange(v: String) =
        _state.update { it.copy(receiveQty = v) }

    fun onReceiveUnitCostChange(v: String) =
        _state.update { it.copy(receiveUnitCost = v) }

    fun onReceiveCurrencyChange(v: CurrencyCode) =
        _state.update { it.copy(receiveCurrency = v) }

    fun onReceiveValuationChange(v: ValuationMethod) =
        _state.update { it.copy(receiveValuation = v) }

    fun onFromWarehouseIdChange(v: String) =
        _state.update { it.copy(fromWarehouseId = v, error = null) }

    fun onToWarehouseIdChange(v: String) =
        _state.update { it.copy(toWarehouseId = v, error = null) }

    fun onTransferNotesChange(v: String) =
        _state.update { it.copy(transferNotes = v) }

    fun onTransferStockItemIdChange(v: String) =
        _state.update { it.copy(transferStockItemId = v) }

    fun onTransferUomIdChange(v: String) =
        _state.update { it.copy(transferUomId = v) }

    fun onTransferQtyChange(v: String) =
        _state.update { it.copy(transferQty = v) }

    fun onTransferEntryIdChange(v: String) =
        _state.update { it.copy(transferEntryId = v, error = null) }

    fun onReconWarehouseIdChange(v: String) =
        _state.update { it.copy(reconWarehouseId = v, error = null) }

    fun onReconScopeChange(v: ReconciliationScope) =
        _state.update { it.copy(reconScope = v) }

    fun onReconCurrencyChange(v: CurrencyCode) =
        _state.update { it.copy(reconCurrency = v, error = null) }

    fun onReconExchangeRateChange(v: String) =
        _state.update { it.copy(reconExchangeRate = v) }

    fun onReconItemIdsChange(v: String) =
        _state.update { it.copy(reconItemIds = v) }

    fun onReconNotesChange(v: String) =
        _state.update { it.copy(reconNotes = v) }

    fun onReconciliationIdChange(v: String) =
        _state.update { it.copy(reconciliationId = v, error = null) }

    fun onReconLineStockItemIdChange(v: String) =
        _state.update { it.copy(reconLineStockItemId = v) }

    fun onReconCountedQtyChange(v: String) =
        _state.update { it.copy(reconCountedQty = v) }

    fun onCancelNotesChange(v: String) =
        _state.update { it.copy(cancelNotes = v) }

    /**
     * CameraX scan → parse inventory QR → lookup stock_item by OEM → fill receive fields.
     */
    fun scanQrForReceive() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val ref = scanAndLookup()
                val valuation = when (ref.valuation) {
                    InventoryQrValuation.AVG -> ValuationMethod.AVG
                    InventoryQrValuation.FIFO -> ValuationMethod.FIFO
                }
                _state.update {
                    it.copy(
                        busy = false,
                        receiveStockItemId = ref.stockItemId,
                        receiveUomId = ref.uomId,
                        receiveValuation = valuation,
                        lastQrPayload = ref.rawPayload,
                        message = "Receive ← QR OEM ${ref.oemPartNumber} → ${ref.stockItemId}",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "QR receive scan failed")
                }
            }
        }
    }

    /**
     * CameraX scan → fill cycle-count line stock item (+ append to partial item list).
     */
    fun scanQrForCycleCount() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val ref = scanAndLookup()
                _state.update { st ->
                    val ids = st.reconItemIds
                        .split(',', ' ', '\n', '\t')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toMutableList()
                    if (ref.stockItemId !in ids) ids.add(ref.stockItemId)
                    st.copy(
                        busy = false,
                        reconLineStockItemId = ref.stockItemId,
                        reconItemIds = ids.joinToString(","),
                        lastQrPayload = ref.rawPayload,
                        message = "Cycle count ← QR OEM ${ref.oemPartNumber} → ${ref.stockItemId}",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "QR cycle-count scan failed")
                }
            }
        }
    }

    fun postReceipt() {
        val wh = _state.value.receiveWarehouseId.trim()
        val item = _state.value.receiveStockItemId.trim()
        val uom = _state.value.receiveUomId.trim()
        val qty = _state.value.receiveQty.trim().toDoubleOrNull()
        val cost = _state.value.receiveUnitCost.trim().toDoubleOrNull()
        when {
            wh.isEmpty() -> {
                _state.update { it.copy(error = "Receive warehouse UUID required") }
                return
            }
            item.isEmpty() || uom.isEmpty() -> {
                _state.update { it.copy(error = "Stock item + UOM UUIDs required") }
                return
            }
            qty == null || qty <= 0 -> {
                _state.update { it.copy(error = "Receive qty must be > 0") }
                return
            }
            cost == null || cost < 0 -> {
                _state.update { it.copy(error = "Unit cost must be ≥ 0") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.postStockReceipt(
                    toWarehouseId = wh,
                    notes = _state.value.receiveNotes.ifBlank { null },
                    lines = listOf(
                        ReceiptLineInput(
                            stockItemId = item,
                            uomId = uom,
                            qty = qty!!,
                            unitCost = cost!!,
                            currency = _state.value.receiveCurrency,
                            valuationMethod = _state.value.receiveValuation,
                        ),
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.POST_STOCK_RECEIPT} " +
                            "(${it.receiveCurrency.rpcValue}) → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "receive failed")
                }
            }
        }
    }

    fun createTransfer() {
        val from = _state.value.fromWarehouseId.trim()
        val to = _state.value.toWarehouseId.trim()
        val item = _state.value.transferStockItemId.trim()
        val uom = _state.value.transferUomId.trim()
        val qty = _state.value.transferQty.trim().toDoubleOrNull()
        when {
            from.isEmpty() || to.isEmpty() -> {
                _state.update { it.copy(error = "From + to warehouse UUIDs required") }
                return
            }
            from == to -> {
                _state.update { it.copy(error = "From and to warehouses must differ") }
                return
            }
            item.isEmpty() || uom.isEmpty() -> {
                _state.update { it.copy(error = "Transfer stock item + UOM required") }
                return
            }
            qty == null || qty <= 0 -> {
                _state.update { it.copy(error = "Transfer qty must be > 0") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createStockTransfer(
                    fromWarehouseId = from,
                    toWarehouseId = to,
                    notes = _state.value.transferNotes.ifBlank { null },
                    lines = listOf(
                        TransferLineInput(
                            stockItemId = item,
                            uomId = uom,
                            qty = qty!!,
                        ),
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        transferEntryId = id,
                        message = "${RpcNames.CREATE_STOCK_TRANSFER} → $id (pending approval)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create transfer failed")
                }
            }
        }
    }

    fun approveTransfer() {
        val entryId = _state.value.transferEntryId.trim()
        if (entryId.isEmpty()) {
            _state.update { it.copy(error = "Transfer entry UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.approveStockTransfer(entryId)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.APPROVE_STOCK_TRANSFER} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "approve failed")
                }
            }
        }
    }

    fun rejectTransfer() {
        val entryId = _state.value.transferEntryId.trim()
        if (entryId.isEmpty()) {
            _state.update { it.copy(error = "Transfer entry UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.rejectStockTransfer(entryId)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.REJECT_STOCK_TRANSFER} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "reject failed")
                }
            }
        }
    }

    fun createReconDraft() {
        val wh = _state.value.reconWarehouseId.trim()
        if (wh.isEmpty()) {
            _state.update { it.copy(error = "Recon warehouse UUID required") }
            return
        }
        val scope = _state.value.reconScope
        val itemIds = _state.value.reconItemIds
            .split(',', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (scope == ReconciliationScope.PARTIAL && itemIds.isEmpty()) {
            _state.update { it.copy(error = "Partial scope needs comma-separated item UUIDs") }
            return
        }
        val currency = _state.value.reconCurrency
        val rate = _state.value.reconExchangeRate.trim().toDoubleOrNull()
        if (currency == CurrencyCode.ZIG && (rate == null || rate <= 0)) {
            _state.update { it.copy(error = "ZIG requires a positive exchange rate") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createStockReconciliationDraft(
                    warehouseId = wh,
                    scope = scope,
                    currency = currency,
                    itemIds = if (scope == ReconciliationScope.PARTIAL) itemIds else null,
                    notes = _state.value.reconNotes.ifBlank { null },
                    exchangeRate = rate ?: if (currency == CurrencyCode.USD) 1.0 else null,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        reconciliationId = id,
                        message = "${RpcNames.CREATE_STOCK_RECONCILIATION_DRAFT} " +
                            "(${it.reconCurrency.rpcValue}) → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create draft failed")
                }
            }
        }
    }

    fun upsertReconLines() {
        val reconId = _state.value.reconciliationId.trim()
        val item = _state.value.reconLineStockItemId.trim()
        val counted = _state.value.reconCountedQty.trim().toDoubleOrNull()
        when {
            reconId.isEmpty() -> {
                _state.update { it.copy(error = "Reconciliation UUID required") }
                return
            }
            item.isEmpty() -> {
                _state.update { it.copy(error = "Line stock item UUID required") }
                return
            }
            counted == null || counted < 0 -> {
                _state.update { it.copy(error = "Counted qty must be ≥ 0") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val n = rpc.upsertStockReconciliationLines(
                    reconciliationId = reconId,
                    lines = listOf(
                        ReconciliationLineInput(
                            stockItemId = item,
                            countedQty = counted!!,
                        ),
                    ),
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.UPSERT_STOCK_RECONCILIATION_LINES} → $n line(s)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "upsert lines failed")
                }
            }
        }
    }

    fun submitRecon() = runReconIdAction(RpcNames.SUBMIT_STOCK_RECONCILIATION) {
        rpc.submitStockReconciliation(it)
    }

    fun approveRecon() = runReconIdAction(RpcNames.APPROVE_STOCK_RECONCILIATION) {
        rpc.approveStockReconciliation(it)
    }

    fun cancelRecon() {
        val reconId = _state.value.reconciliationId.trim()
        if (reconId.isEmpty()) {
            _state.update { it.copy(error = "Reconciliation UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.cancelStockReconciliation(
                    reconciliationId = reconId,
                    notes = _state.value.cancelNotes.ifBlank { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.CANCEL_STOCK_RECONCILIATION} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "cancel failed")
                }
            }
        }
    }

    private fun runReconIdAction(rpcName: String, block: suspend (String) -> String) {
        val reconId = _state.value.reconciliationId.trim()
        if (reconId.isEmpty()) {
            _state.update { it.copy(error = "Reconciliation UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = block(reconId)
                _state.update {
                    it.copy(busy = false, message = "$rpcName → $id")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "$rpcName failed")
                }
            }
        }
    }

    private data class QrStockRef(
        val stockItemId: String,
        val uomId: String,
        val oemPartNumber: String,
        val valuation: InventoryQrValuation,
        val rawPayload: String,
    )

    private suspend fun scanAndLookup(): QrStockRef {
        var status = qr.getCameraPermissionStatus()
        if (status != CameraPermissionStatus.GRANTED) {
            status = qr.requestCameraPermission()
        }
        if (status != CameraPermissionStatus.GRANTED) {
            throw SecurityException("Camera permission required for QR scan ($status)")
        }
        val scan = qr.scanOnce()
        val fields = parseInventoryQrPayload(scan.rawValue)
        val item = rpc.lookupStockItemByOem(fields.oemPartNumber)
        return QrStockRef(
            stockItemId = item.stockItemId,
            uomId = item.uomId,
            oemPartNumber = item.oemPartNumber,
            valuation = fields.valuation,
            rawPayload = scan.rawValue,
        )
    }

    companion object {
        fun factory(rpc: RpcClient, qr: QrScannerBridge): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WarehouseViewModel(rpc, qr) as T
            }
    }
}
