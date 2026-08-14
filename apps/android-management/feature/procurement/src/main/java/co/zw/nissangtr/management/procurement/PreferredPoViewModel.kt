package co.zw.nissangtr.management.procurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.qr.CameraPermissionStatus
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.bridges.qr.parseInventoryQrPayload
import co.zw.nissangtr.management.rpc.BlanketLineInput
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.PreferredSupplierRef
import co.zw.nissangtr.management.rpc.ProcurementProgressStep
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.WarehouseRef
import co.zw.nissangtr.management.rpc.pickReceivingWarehouse
import co.zw.nissangtr.management.rpc.resolveProcurementProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One quoted line on the preferred-supplier PO draft (staff-entered unit cost). */
data class PreferredPoLineDraft(
    val stockItemId: String,
    val uomId: String,
    val oemPartNumber: String?,
    val qty: Double,
    val unitPrice: Double,
)

data class PreferredPoUiState(
    val suppliers: List<PreferredSupplierRef> = emptyList(),
    val warehouses: List<WarehouseRef> = emptyList(),
    val supplierId: String = "",
    val warehouseId: String = "",
    val currency: CurrencyCode = CurrencyCode.USD,
    val exchangeRate: String = "1",
    val notes: String = "",
    val expectedDate: String = "",
    val lineStockItemId: String = "",
    val lineUomId: String = "",
    val lineOem: String = "",
    val lineQty: String = "1",
    val lineUnitPrice: String = "0",
    val lines: List<PreferredPoLineDraft> = emptyList(),
    val createdPoId: String? = null,
    val progressStep: ProcurementProgressStep = ProcurementProgressStep.DRAFT,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * H2 — preferred-supplier manual PO (not RFQ-gated).
 * Wires [RpcClient.listPreferredSuppliers], [RpcClient.createPurchaseOrder],
 * [RpcClient.submitPurchaseOrder] — parity with web `preferred-po.ts`.
 */
class PreferredPoViewModel(
    private val rpc: RpcClient,
    private val qr: QrScannerBridge?,
) : ViewModel() {
    private val _state = MutableStateFlow(PreferredPoUiState())
    val state: StateFlow<PreferredPoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { boot() }
    }

    fun onSupplierIdChange(id: String) {
        val sup = _state.value.suppliers.find { it.id == id }
        _state.update {
            it.copy(
                supplierId = id,
                currency = sup?.defaultCurrency ?: it.currency,
            )
        }
    }

    fun onWarehouseIdChange(v: String) = _state.update { it.copy(warehouseId = v) }
    fun onCurrencyChange(v: CurrencyCode) = _state.update { it.copy(currency = v) }
    fun onExchangeRateChange(v: String) = _state.update { it.copy(exchangeRate = v) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onExpectedDateChange(v: String) = _state.update { it.copy(expectedDate = v) }
    fun onLineStockItemIdChange(v: String) = _state.update { it.copy(lineStockItemId = v) }
    fun onLineUomIdChange(v: String) = _state.update { it.copy(lineUomId = v) }
    fun onLineOemChange(v: String) = _state.update { it.copy(lineOem = v) }
    fun onLineQtyChange(v: String) = _state.update { it.copy(lineQty = v) }
    fun onLineUnitPriceChange(v: String) = _state.update { it.copy(lineUnitPrice = v) }

    fun resolveOem() {
        val oem = _state.value.lineOem.trim()
        if (oem.isEmpty()) {
            _state.update { it.copy(error = "Enter OEM part number") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val ref = rpc.lookupStockItemByOem(oem)
                _state.update {
                    it.copy(
                        busy = false,
                        lineStockItemId = ref.stockItemId,
                        lineUomId = ref.uomId,
                        lineOem = ref.oemPartNumber,
                        message = "Resolved OEM ${ref.oemPartNumber}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "OEM lookup failed") }
            }
        }
    }

    fun scanQrForLine() {
        val bridge = qr ?: run {
            _state.update { it.copy(error = "QR bridge unavailable") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                var status = bridge.getCameraPermissionStatus()
                if (status != CameraPermissionStatus.GRANTED) {
                    status = bridge.requestCameraPermission()
                }
                if (status != CameraPermissionStatus.GRANTED) {
                    throw SecurityException("Camera permission required for QR scan ($status)")
                }
                val scan = bridge.scanOnce()
                val fields = parseInventoryQrPayload(scan.rawValue)
                val item = rpc.lookupStockItemByOem(fields.oemPartNumber)
                _state.update {
                    it.copy(
                        busy = false,
                        lineStockItemId = item.stockItemId,
                        lineUomId = item.uomId,
                        lineOem = item.oemPartNumber,
                        message = "Line ← QR OEM ${item.oemPartNumber}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "QR scan failed") }
            }
        }
    }

    fun addLine() {
        val s = _state.value
        val qty = s.lineQty.toDoubleOrNull()
        val price = s.lineUnitPrice.toDoubleOrNull()
        when {
            s.lineStockItemId.isBlank() || s.lineUomId.isBlank() -> {
                _state.update { it.copy(error = "Stock item + UOM required (resolve OEM or scan QR)") }
            }
            qty == null || qty <= 0 -> {
                _state.update { it.copy(error = "Positive qty required") }
            }
            price == null || price < 0 -> {
                _state.update { it.copy(error = "Quoted unit cost required (staff-entered)") }
            }
            else -> {
                _state.update {
                    it.copy(
                        error = null,
                        lines = it.lines + PreferredPoLineDraft(
                            stockItemId = s.lineStockItemId.trim(),
                            uomId = s.lineUomId.trim(),
                            oemPartNumber = s.lineOem.trim().ifBlank { null },
                            qty = qty,
                            unitPrice = price,
                        ),
                        lineQty = "1",
                        lineUnitPrice = "0",
                        message = "Line added (${it.lines.size + 1})",
                    )
                }
            }
        }
    }

    fun removeLine(index: Int) {
        _state.update {
            if (index !in it.lines.indices) return@update it
            it.copy(lines = it.lines.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun createDraft() = create(submit = false)

    fun createAndSubmit() = create(submit = true)

    private fun create(submit: Boolean) {
        val s = _state.value
        if (s.supplierId.isBlank()) {
            _state.update { it.copy(error = "Select a preferred supplier") }
            return
        }
        if (s.warehouseId.isBlank()) {
            _state.update { it.copy(error = "Select receiving warehouse") }
            return
        }
        if (s.lines.isEmpty()) {
            _state.update { it.copy(error = "Add at least one quoted line") }
            return
        }
        val rate = s.exchangeRate.toDoubleOrNull() ?: 1.0
        if (s.currency == CurrencyCode.ZIG && rate <= 0) {
            _state.update { it.copy(error = "Positive exchange rate required for ZIG") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = rpc.createPurchaseOrder(
                    supplierId = s.supplierId.trim(),
                    warehouseId = s.warehouseId.trim(),
                    currency = s.currency,
                    exchangeRate = rate,
                    lines = s.lines.map { line ->
                        BlanketLineInput(
                            stockItemId = line.stockItemId,
                            uomId = line.uomId,
                            qty = line.qty,
                            unitPrice = line.unitPrice,
                            currency = s.currency,
                        )
                    },
                    notes = s.notes.trim().ifBlank { null },
                    expectedDate = s.expectedDate.trim().ifBlank { null },
                )
                var status = "draft"
                if (submit) {
                    rpc.submitPurchaseOrder(id)
                    status = "submitted"
                }
                val step = resolveProcurementProgress(status = status)
                _state.update {
                    it.copy(
                        busy = false,
                        createdPoId = id,
                        progressStep = step,
                        lines = if (submit) emptyList() else it.lines,
                        message = if (submit) {
                            "${RpcNames.SUBMIT_PURCHASE_ORDER} → $id"
                        } else {
                            "${RpcNames.CREATE_PURCHASE_ORDER} draft → $id"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create preferred PO failed")
                }
            }
        }
    }

    private suspend fun boot() {
        _state.update { it.copy(busy = true, error = null) }
        try {
            val suppliers = runCatching { rpc.listPreferredSuppliers() }.getOrDefault(emptyList())
            val warehouses = runCatching { rpc.listWarehouses() }.getOrDefault(emptyList())
            val recv = pickReceivingWarehouse(warehouses)
            val first = suppliers.firstOrNull()
            _state.update {
                it.copy(
                    busy = false,
                    suppliers = suppliers,
                    warehouses = warehouses,
                    supplierId = first?.id
                        ?: if (rpc is FakeRpcClient) FakeRpcClient.FAKE_SUPPLIER_ID else "",
                    warehouseId = recv?.id
                        ?: if (rpc is FakeRpcClient) {
                            warehouses.find { w -> w.code == "WH1" }?.id
                                ?: FakeRpcClient.FAKE_WAREHOUSE_ID
                        } else {
                            ""
                        },
                    currency = first?.defaultCurrency ?: CurrencyCode.USD,
                    lineStockItemId = if (rpc is FakeRpcClient) FakeRpcClient.FAKE_STOCK_ITEM_ID else "",
                    lineUomId = if (rpc is FakeRpcClient) {
                        "00000000-0000-4000-8000-0000000000u1"
                    } else {
                        ""
                    },
                    lineOem = if (rpc is FakeRpcClient) "21410-JF00A" else "",
                    message = when {
                        suppliers.isEmpty() -> "No preferred suppliers — mark roster on web first"
                        else -> "${suppliers.size} preferred supplier(s)"
                    },
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(busy = false, error = e.message ?: "boot failed") }
        }
    }

    companion object {
        fun factory(rpc: RpcClient, qr: QrScannerBridge?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PreferredPoViewModel(rpc, qr) as T
            }
    }
}
