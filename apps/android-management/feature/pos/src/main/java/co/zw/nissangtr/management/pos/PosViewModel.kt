package co.zw.nissangtr.management.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.escpos.BluetoothPermissionStatus
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosReceiptLine
import co.zw.nissangtr.bridges.qr.CameraPermissionStatus
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.rpc.CatalogPartHit
import co.zw.nissangtr.management.rpc.CatalogSearchMode
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.CustomerOption
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.FulfillmentMode
import co.zw.nissangtr.management.rpc.PosCartLineSummary
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.WarehouseRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PosWorkspaceMode {
    /** Full standalone till — search / catalog / cart / checkout (no pairing). */
    Till,
    /** Optional phone companion: claim pairing code → bridge scan into shared cart. */
    Companion,
}

data class PosUiState(
    val mode: PosWorkspaceMode = PosWorkspaceMode.Till,
    val warehouses: List<WarehouseRef> = emptyList(),
    val warehouseId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerQuery: String = "",
    val customerHits: List<CustomerOption> = emptyList(),
    val currency: CurrencyCode = CurrencyCode.USD,
    val fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
    val cartId: String = "",
    val cartLines: List<PosCartLineSummary> = emptyList(),
    val searchMode: CatalogSearchMode = CatalogSearchMode.PART,
    val searchQuery: String = "",
    val searchHits: List<CatalogPartHit> = emptyList(),
    val addQty: String = "1",
    val receiptEmail: String = "",
    val receiptWhatsapp: String = "",
    val pairingCodeDisplay: String = "",
    val scanSessionId: String = "",
    val pairingExpiresAt: String = "",
    val companionPairingInput: String = "",
    val companionCartId: String = "",
    val companionSessionId: String = "",
    val printerMac: String = "",
    val printerConnected: Boolean = false,
    val lastInvoiceId: String? = null,
    val lastBindMessage: String? = null,
    val lastQrPayload: String? = null,
    val isSalesHome: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Standalone sales POS + optional Scan companion.
 * Line add via search/catalog never requires [RpcNames.CREATE_POS_SCAN_SESSION].
 * QR only through [QrScannerBridge] (Bridge-First).
 */
class PosViewModel(
    private val rpc: RpcClient,
    private val qr: QrScannerBridge,
    private val printer: EscPosPrinterBridge,
) : ViewModel() {
    private val _state = MutableStateFlow(PosUiState())
    val state: StateFlow<PosUiState> = _state.asStateFlow()

    private var cartPollJob: Job? = null

    init {
        viewModelScope.launch {
            loadWarehouses()
        }
    }

    fun setSalesHome(isSalesHome: Boolean) =
        _state.update { it.copy(isSalesHome = isSalesHome) }

    fun setMode(mode: PosWorkspaceMode) {
        _state.update { it.copy(mode = mode, error = null, message = null) }
        if (mode == PosWorkspaceMode.Till && _state.value.cartId.isNotBlank()) {
            startCartPolling(_state.value.cartId)
        } else if (mode == PosWorkspaceMode.Companion) {
            cartPollJob?.cancel()
        }
    }

    fun onWarehouseIdChange(v: String) =
        _state.update { it.copy(warehouseId = v, error = null) }

    fun onCustomerIdChange(v: String) =
        _state.update { it.copy(customerId = v) }

    fun onCurrencyChange(v: CurrencyCode) =
        _state.update { it.copy(currency = v, error = null) }

    fun onFulfillmentModeChange(v: FulfillmentMode) =
        _state.update { it.copy(fulfillmentMode = v) }

    fun onSearchModeChange(v: CatalogSearchMode) =
        _state.update { it.copy(searchMode = v) }

    fun onSearchQueryChange(v: String) =
        _state.update { it.copy(searchQuery = v) }

    fun onAddQtyChange(v: String) =
        _state.update { it.copy(addQty = v) }

    fun onReceiptEmailChange(v: String) =
        _state.update { it.copy(receiptEmail = v) }

    fun onReceiptWhatsappChange(v: String) =
        _state.update { it.copy(receiptWhatsapp = v) }

    fun onCompanionPairingInputChange(v: String) =
        _state.update { it.copy(companionPairingInput = v.filter { ch -> ch.isDigit() }.take(6)) }

    fun onPrinterMacChange(v: String) =
        _state.update { it.copy(printerMac = v) }

    fun selectWarehouse(ref: WarehouseRef) =
        _state.update { it.copy(warehouseId = ref.id, error = null) }

    private suspend fun loadWarehouses() {
        try {
            val list = rpc.listWarehouses()
            _state.update { s ->
                s.copy(
                    warehouses = list,
                    warehouseId = s.warehouseId.ifBlank {
                        list.firstOrNull()?.id ?: FakeRpcClient.FAKE_WAREHOUSE_ID
                    },
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    warehouseId = it.warehouseId.ifBlank { FakeRpcClient.FAKE_WAREHOUSE_ID },
                    error = e.message ?: "warehouse list failed",
                )
            }
        }
    }

    fun createCart() {
        val warehouseId = _state.value.warehouseId.trim()
        if (warehouseId.isEmpty()) {
            _state.update { it.copy(error = "Warehouse required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val id = rpc.createPosCart(
                    warehouseId = warehouseId,
                    currency = _state.value.currency,
                    fulfillmentMode = _state.value.fulfillmentMode,
                    customerId = _state.value.customerId.trim().ifBlank { null },
                )
                _state.update {
                    it.copy(
                        busy = false,
                        cartId = id,
                        cartLines = emptyList(),
                        pairingCodeDisplay = "",
                        scanSessionId = "",
                        lastInvoiceId = null,
                        lastBindMessage = null,
                        message = "Open cart $id (no pairing required)",
                    )
                }
                startCartPolling(id)
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create cart failed")
                }
            }
        }
    }

    fun searchCatalog() {
        val q = _state.value.searchQuery.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(error = "Enter a search query") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val result = rpc.searchCatalog(_state.value.searchMode, q)
                _state.update {
                    it.copy(
                        busy = false,
                        searchHits = result.parts,
                        message = if (result.parts.isEmpty()) {
                            "No catalog hits for “$q”"
                        } else {
                            "${result.parts.size} part(s) — tap Add to open cart"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "search failed")
                }
            }
        }
    }

    /** Resolve OEM via stock_items → [RpcNames.ADD_CART_LINE] (standalone; no session). */
    fun addPartFromCatalog(hit: CatalogPartHit) {
        val cartId = _state.value.cartId.trim()
        val qty = _state.value.addQty.trim().toDoubleOrNull()
        when {
            cartId.isEmpty() -> {
                _state.update { it.copy(error = "Create an open cart first") }
                return
            }
            qty == null || qty <= 0 -> {
                _state.update { it.copy(error = "Qty must be a positive number") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val ref = rpc.lookupStockItemByOem(hit.oemPartNumber)
                val lineId = rpc.addCartLine(
                    cartId = cartId,
                    stockItemId = ref.stockItemId,
                    uomId = ref.uomId,
                    qty = qty!!,
                )
                refreshCartLines(cartId)
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Added ${hit.oemPartNumber} → line $lineId",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = e.message
                            ?: "Add failed — OEM may not be in stock_items",
                    )
                }
            }
        }
    }

    fun refreshCart() {
        val cartId = _state.value.cartId.trim()
        if (cartId.isEmpty()) return
        viewModelScope.launch {
            try {
                refreshCartLines(cartId)
                _state.update { it.copy(message = "Cart refreshed (${it.cartLines.size} lines)") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "refresh failed") }
            }
        }
    }

    private suspend fun refreshCartLines(cartId: String) {
        val lines = rpc.listPosCartLines(cartId)
        _state.update { it.copy(cartLines = lines) }
    }

    private fun startCartPolling(cartId: String) {
        cartPollJob?.cancel()
        cartPollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    refreshCartLines(cartId)
                } catch (_: Exception) {
                    // poll soft-fail — companion Realtime not wired; poll is fallback
                }
                delay(CART_POLL_MS)
            }
        }
    }

    /** Optional: owner shows pairing code for phone companion. */
    fun createPairingSession() {
        val cartId = _state.value.cartId.trim()
        if (cartId.isEmpty()) {
            _state.update { it.copy(error = "Open cart required before pairing") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val session = rpc.createPosScanSession(cartId)
                _state.update {
                    it.copy(
                        busy = false,
                        scanSessionId = session.sessionId,
                        pairingCodeDisplay = session.pairingCode,
                        pairingExpiresAt = session.expiresAt,
                        message = "Pairing code ${session.pairingCode} — claim on phone companion",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "pairing create failed")
                }
            }
        }
    }

    fun revokePairingSession() {
        val sessionId = _state.value.scanSessionId.trim()
        if (sessionId.isEmpty()) {
            _state.update { it.copy(error = "No active pairing session") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                rpc.revokePosScanSession(sessionId)
                _state.update {
                    it.copy(
                        busy = false,
                        scanSessionId = "",
                        pairingCodeDisplay = "",
                        pairingExpiresAt = "",
                        message = "Companion pairing revoked",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "revoke failed")
                }
            }
        }
    }

    fun claimCompanionSession() {
        val code = _state.value.companionPairingInput.trim()
        if (!code.matches(Regex("^\\d{6}$"))) {
            _state.update { it.copy(error = "Enter the 6-digit pairing code") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val sessionId = rpc.claimPosScanSession(code)
                val cartId = rpc.getPosScanSessionCartId(sessionId)
                    ?: _state.value.cartId.trim().ifBlank { null }
                _state.update {
                    it.copy(
                        busy = false,
                        companionSessionId = sessionId,
                        companionCartId = cartId.orEmpty(),
                        message = if (cartId != null) {
                            "Session claimed — cart $cartId — scan via bridge"
                        } else {
                            "Session claimed — paste cart UUID if scan fails"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "claim failed")
                }
            }
        }
    }

    fun onCompanionCartIdChange(v: String) =
        _state.update { it.copy(companionCartId = v, error = null) }

    /** Bridge scan → [RpcNames.ADD_CART_LINE_FROM_QR] on claimed companion cart. */
    fun companionScanAddLine() {
        val cartId = _state.value.companionCartId.trim()
            .ifBlank { _state.value.cartId.trim() }
        val qty = _state.value.addQty.trim().toDoubleOrNull() ?: 1.0
        if (cartId.isEmpty()) {
            _state.update {
                it.copy(error = "Cart UUID required (paste from till device)")
            }
            return
        }
        if (qty <= 0) {
            _state.update { it.copy(error = "Qty must be a positive number") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                ensureCamera()
                val scan = qr.scanOnce()
                val lineId = rpc.addCartLineFromQr(
                    cartId = cartId,
                    qrPayload = scan.rawValue,
                    qty = qty,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        companionCartId = cartId,
                        lastQrPayload = scan.rawValue,
                        message = "${RpcNames.ADD_CART_LINE_FROM_QR} → $lineId",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "companion scan failed")
                }
            }
        }
    }

    /** Optional on-till Bridge QR (standalone staff — no session required). */
    fun tillScanAddLine() {
        val cartId = _state.value.cartId.trim()
        val qty = _state.value.addQty.trim().toDoubleOrNull() ?: 1.0
        if (cartId.isEmpty()) {
            _state.update { it.copy(error = "Open cart before QR scan") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                ensureCamera()
                val scan = qr.scanOnce()
                val lineId = rpc.addCartLineFromQr(
                    cartId = cartId,
                    qrPayload = scan.rawValue,
                    qty = qty,
                )
                refreshCartLines(cartId)
                _state.update {
                    it.copy(
                        busy = false,
                        lastQrPayload = scan.rawValue,
                        message = "Scanned → line $lineId",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "QR add failed")
                }
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
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                ensureBluetooth()
                printer.configurePrinterAddress(mac)
                printer.connect()
                _state.update {
                    it.copy(
                        busy = false,
                        printerConnected = true,
                        message = "ESC/POS printer connected ($mac)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        printerConnected = false,
                        error = e.message ?: "printer connect failed",
                    )
                }
            }
        }
    }

    fun checkout() {
        val cartId = _state.value.cartId.trim()
        if (cartId.isEmpty()) {
            _state.update { it.copy(error = "Open cart required") }
            return
        }
        if (_state.value.cartLines.isEmpty()) {
            _state.update { it.copy(error = "Add at least one line before checkout") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val result = rpc.checkoutPosCart(
                    cartId = cartId,
                    receiptEmail = _state.value.receiptEmail.trim().ifBlank { null },
                    receiptWhatsappE164 = _state.value.receiptWhatsapp.trim().ifBlank { null },
                    receiptPhoneE164 = _state.value.receiptWhatsapp.trim().ifBlank { null },
                )
                cartPollJob?.cancel()
                var msg = "${RpcNames.CHECKOUT_POS_CART} → ${result.invoiceId}"
                msg += " · ${result.bindMessage}"
                if (printer.isConnected()) {
                    try {
                        printer.printReceiptLines(
                            listOf(
                                EscPosReceiptLine("GTR Auto POS", emphasis = true),
                                EscPosReceiptLine("Invoice: ${result.invoiceId}"),
                                EscPosReceiptLine("Currency: ${_state.value.currency.rpcValue}"),
                                EscPosReceiptLine(result.bindMessage),
                                EscPosReceiptLine("Thank you"),
                            ),
                        )
                        msg += " · receipt printed"
                    } catch (pe: Exception) {
                        msg += " · receipt print skipped (${pe.message})"
                    }
                }
                _state.update {
                    it.copy(
                        busy = false,
                        cartId = "",
                        cartLines = emptyList(),
                        pairingCodeDisplay = "",
                        scanSessionId = "",
                        lastInvoiceId = result.invoiceId,
                        lastBindMessage = result.bindMessage,
                        message = msg,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "checkout failed")
                }
            }
        }
    }

    private suspend fun ensureCamera() {
        var status = qr.getCameraPermissionStatus()
        if (status != CameraPermissionStatus.GRANTED) {
            status = qr.requestCameraPermission()
        }
        if (status != CameraPermissionStatus.GRANTED) {
            throw SecurityException("Camera permission required for QR scan ($status)")
        }
    }

    private suspend fun ensureBluetooth() {
        var status = printer.getBluetoothPermissionStatus()
        if (status != BluetoothPermissionStatus.GRANTED) {
            status = printer.requestBluetoothPermission()
        }
        if (status != BluetoothPermissionStatus.GRANTED) {
            throw SecurityException("Bluetooth permission required ($status)")
        }
    }

    override fun onCleared() {
        cartPollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val CART_POLL_MS = 4_000L

        fun factory(
            rpc: RpcClient,
            qr: QrScannerBridge,
            printer: EscPosPrinterBridge,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PosViewModel(rpc, qr, printer) as T
            }
    }
}
