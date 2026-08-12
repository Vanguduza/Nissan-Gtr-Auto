package co.zw.nissangtr.management.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.escpos.BluetoothPermissionStatus
import co.zw.nissangtr.bridges.escpos.BondedEscPosDevice
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosReceiptLine
import co.zw.nissangtr.bridges.qr.CameraPermissionStatus
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.pos.offline.LocalCartLine
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncEngine
import co.zw.nissangtr.management.rpc.CatalogPartHit
import co.zw.nissangtr.management.rpc.CatalogSearchMode
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.CustomerOption
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.FulfillmentMode
import co.zw.nissangtr.management.rpc.PosCartLineSummary
import co.zw.nissangtr.management.rpc.PosQuotationSummary
import co.zw.nissangtr.management.rpc.PosTenderLine
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.management.rpc.WarehouseRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class PosWorkspaceMode {
    /** Full standalone till — search / catalog / cart / checkout (no pairing). */
    Till,
    /** Optional phone companion: claim pairing code → bridge scan into shared cart. */
    Companion,
}

data class PosTenderDraft(
    val tender: String = "cash",
    val amount: String = "",
)

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
    /** Split-bill tenders; empty → classic checkout. */
    val tenderLines: List<PosTenderDraft> = listOf(PosTenderDraft()),
    val ecocashMsisdn: String = "",
    val lastInvoiceTotal: Double = 0.0,
    val pairingCodeDisplay: String = "",
    val scanSessionId: String = "",
    val pairingExpiresAt: String = "",
    val companionPairingInput: String = "",
    val companionCartId: String = "",
    val companionSessionId: String = "",
    val printerMac: String = "",
    val printerConnected: Boolean = false,
    val bondedPrinters: List<BondedEscPosDevice> = emptyList(),
    val lastInvoiceId: String? = null,
    val lastBindMessage: String? = null,
    val lastQrPayload: String? = null,
    val isSalesHome: Boolean = false,
    /** Manager reauth dialog for discount / void / refund / price override. */
    val managerPrompt: ManagerPrompt? = null,
    val discountPercent: String = "10",
    val overrideLineId: String = "",
    val overrideUnitPrice: String = "",
    val managerIdentifier: String = "",
    val managerPassword: String = "",
    val quotations: List<PosQuotationSummary> = emptyList(),
    val quoteNotes: String = "",
    val quoteSendChannel: String = "print",
    val quoteSendContact: String = "",
    val showQuotes: Boolean = false,
    val parkedCartId: String = "",
    /** True when device has no validated internet (or forced offline for demos). */
    val isOffline: Boolean = false,
    /** Queued offline cash sales waiting for replay. */
    val pendingOfflineSales: Int = 0,
    val offlineSnapshotAgeLabel: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

enum class ManagerPrompt {
    Discount,
    VoidCart,
    Refund,
    PriceOverride,
}

/**
 * Standalone sales POS + optional Scan companion.
 * Line add via search/catalog never requires [RpcNames.CREATE_POS_SCAN_SESSION].
 * QR only through [QrScannerBridge] (Bridge-First).
 *
 * TODO(structural-critique.md §1): this ViewModel is the "God ViewModel" flagged by
 * `docs/audit/2026-08-04-master-audit/structural-critique.md` — one 1,550+ line class, one
 * `PosUiState` with 50+ fields, one `viewModelScope`, covering till/cart/checkout, companion
 * pairing, printer, manager reauth, quotations, and offline sync. The pure cart-line math
 * (total calc, offline qty-delta, offline→summary mapping) has been extracted to
 * [PosCartLineOps] as the safe slice of the audit's recommended split. A full decomposition
 * into `PosCartViewModel` / `PosCompanionViewModel` / `PosManagerAuthViewModel` /
 * `PosQuotationViewModel` was deliberately NOT attempted in this pass: checkout, manager
 * reauth (discount/void/refund/price-override), and offline replay all read/write the same
 * `cartId`/`cartLines`/`offlineLocalLines` inside one `StateFlow`, this file has zero existing
 * tests, and the audit itself (§2) notes the God `RpcClient` interface should be split first
 * or a ViewModel split just moves the same 102-method dependency into more files. Splitting
 * StateFlow ownership here without tests risks breaking offline-sync/companion/manager-reauth
 * silently. Needs its own planned pass (`/planner` → approved plan → `@management_app_agent`
 * → `/verifier`), not a by-product of a layout change.
 */
class PosViewModel(
    private val rpc: RpcClient,
    private val qr: QrScannerBridge,
    private val printer: EscPosPrinterBridge,
    private val offlineEngine: OfflinePosSyncEngine? = null,
    private val onlineFlow: Flow<Boolean>? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PosUiState())
    val state: StateFlow<PosUiState> = _state.asStateFlow()

    private var cartPollJob: Job? = null
    /** Local-only cart lines while [PosUiState.isOffline] (includes uom for replay). */
    private val offlineLocalLines = mutableListOf<LocalCartLine>()

    init {
        viewModelScope.launch {
            loadWarehouses()
            // Batch 1 §1.2 — open cart invisibly (parity with web POS)
            if (_state.value.cartId.isBlank() && _state.value.warehouseId.isNotBlank()) {
                ensureOpenCart(showMessage = false)
            }
            refreshOfflineBadge()
        }
        if (onlineFlow != null) {
            viewModelScope.launch {
                onlineFlow.collect { online ->
                    val wasOffline = _state.value.isOffline
                    _state.update { it.copy(isOffline = !online) }
                    if (online && wasOffline) {
                        onBackOnline()
                    }
                    refreshOfflineBadge()
                }
            }
        }
    }

    private fun refreshOfflineBadge() {
        val pending = offlineEngine?.pendingCount() ?: 0
        val wh = _state.value.warehouseId
        val age = if (wh.isNotBlank() && offlineEngine != null) {
            // Engine does not expose pulled-at; badge uses pending count primarily.
            if (pending > 0) "$pending queued" else null
        } else {
            null
        }
        _state.update {
            it.copy(
                pendingOfflineSales = pending,
                offlineSnapshotAgeLabel = age,
            )
        }
    }

    private suspend fun onBackOnline() {
        val engine = offlineEngine ?: return
        val wh = _state.value.warehouseId.trim()
        try {
            if (wh.isNotBlank()) {
                engine.pullSnapshot(wh)
            }
            val drain = engine.drainQueue()
            refreshOfflineBadge()
            val msg = buildString {
                append("Back online")
                if (drain.synced > 0) append(" · synced ${drain.synced} offline sale(s)")
                if (drain.conflicts > 0) append(" · ${drain.conflicts} conflict(s)")
                if (drain.failed > 0) append(" · ${drain.failed} failed")
                if (drain.remaining > 0) append(" · ${drain.remaining} remaining")
            }
            _state.update { it.copy(message = msg, error = null) }
        } catch (e: Exception) {
            _state.update {
                it.copy(error = e.message ?: "offline sync failed")
            }
        }
    }

    fun pullOfflineSnapshot() {
        val engine = offlineEngine ?: return
        val wh = _state.value.warehouseId.trim()
        if (wh.isEmpty()) {
            _state.update { it.copy(error = "Select warehouse before snapshot pull") }
            return
        }
        if (_state.value.isOffline) {
            _state.update { it.copy(error = "Connect to pull catalog snapshot") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val snap = engine.pullSnapshot(wh)
                refreshOfflineBadge()
                _state.update {
                    it.copy(
                        busy = false,
                        message = "Offline snapshot: ${snap.items.size} item(s) cached",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "snapshot pull failed")
                }
            }
        }
    }

    fun syncOfflineQueue() {
        if (_state.value.isOffline) {
            _state.update { it.copy(error = "Connect to sync queued sales") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                onBackOnline()
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "sync failed")
                }
            }
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
        _state.update { it.copy(customerId = v, customerName = if (v.isBlank()) "" else it.customerName) }

    fun onCustomerQueryChange(v: String) =
        _state.update { it.copy(customerQuery = v, error = null) }

    fun selectCustomer(c: CustomerOption) =
        _state.update {
            it.copy(
                customerId = c.id,
                customerName = c.displayName,
                customerQuery = c.displayName,
                customerHits = emptyList(),
            )
        }

    fun clearCustomer() =
        _state.update {
            it.copy(customerId = "", customerName = "", customerQuery = "", customerHits = emptyList())
        }

    fun searchCustomers() {
        val q = _state.value.customerQuery
        viewModelScope.launch {
            try {
                val hits = rpc.searchCustomers(q)
                _state.update {
                    it.copy(
                        customerHits = hits,
                        message = if (hits.isEmpty()) "No customers matched" else null,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "customer search failed") }
            }
        }
    }

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

    fun onTenderChange(index: Int, tender: String) {
        _state.update { st ->
            val next = st.tenderLines.toMutableList()
            if (index in next.indices) {
                next[index] = next[index].copy(tender = tender)
            }
            st.copy(tenderLines = next)
        }
    }

    fun onTenderAmountChange(index: Int, amount: String) {
        _state.update { st ->
            val next = st.tenderLines.toMutableList()
            if (index in next.indices) {
                next[index] = next[index].copy(amount = amount)
            }
            st.copy(tenderLines = next)
        }
    }

    fun addTenderLine() {
        _state.update { it.copy(tenderLines = it.tenderLines + PosTenderDraft()) }
    }

    fun removeTenderLine(index: Int) {
        _state.update { st ->
            if (st.tenderLines.size <= 1) st
            else st.copy(tenderLines = st.tenderLines.filterIndexed { i, _ -> i != index })
        }
    }

    fun onEcocashMsisdnChange(v: String) =
        _state.update { it.copy(ecocashMsisdn = v, error = null) }

    fun chargeEcocashDirect() {
        val invoiceId = _state.value.lastInvoiceId?.trim().orEmpty()
        val msisdn = _state.value.ecocashMsisdn.trim()
        val amount = _state.value.lastInvoiceTotal
        if (invoiceId.isEmpty()) {
            _state.update { it.copy(error = "Checkout an invoice first") }
            return
        }
        if (msisdn.isEmpty()) {
            _state.update { it.copy(error = "Enter customer EcoCash number") }
            return
        }
        if (amount <= 0) {
            _state.update { it.copy(error = "Invoice total unknown — re-checkout") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val intentId = rpc.createEcocashIntent(
                    externalRef = "POS-$invoiceId-${System.currentTimeMillis()}",
                    payerMsisdn = msisdn,
                    amount = amount,
                    currency = _state.value.currency,
                    payerMode = "pos_entered",
                    customerId = _state.value.customerId.ifBlank { null },
                    salesInvoiceId = invoiceId,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        message = "${RpcNames.CREATE_ECOCASH_INTENT} → $intentId — " +
                            "customer approves EcoCash PIN on $msisdn (direct, not ContiPay/Paynow)",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "EcoCash intent failed")
                }
            }
        }
    }

    fun onCompanionPairingInputChange(v: String) =
        _state.update { it.copy(companionPairingInput = v.filter { ch -> ch.isDigit() }.take(6)) }

    fun onPrinterMacChange(v: String) =
        _state.update { it.copy(printerMac = v) }

    fun selectWarehouse(ref: WarehouseRef) =
        _state.update { it.copy(warehouseId = ref.id, error = null) }

    private suspend fun loadWarehouses() {
        try {
            // WH2 storefloor only — WH1 receiving must not appear in POS picker.
            val list = rpc.listSaleableWarehouses()
            _state.update { s ->
                val selectedStillValid = list.any { it.id == s.warehouseId }
                s.copy(
                    warehouses = list,
                    warehouseId = when {
                        selectedStillValid -> s.warehouseId
                        list.isNotEmpty() -> list.first().id
                        else -> ""
                    },
                    error = if (list.isEmpty()) {
                        "No WH2 storefloor warehouses available for POS"
                    } else {
                        s.error
                    },
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    warehouses = emptyList(),
                    warehouseId = "",
                    error = e.message ?: "warehouse list failed",
                )
            }
        }
    }

    fun createCart() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            ensureOpenCart(showMessage = true)
        }
    }

    /** Returns true when an open cart id is present after the call. */
    private suspend fun ensureOpenCart(showMessage: Boolean): Boolean {
        if (_state.value.cartId.isNotBlank()) return true
        val warehouseId = _state.value.warehouseId.trim()
        if (warehouseId.isEmpty()) {
            _state.update {
                it.copy(busy = false, error = "Warehouse required")
            }
            return false
        }
        if (_state.value.isOffline) {
            val id = "offline-${UUID.randomUUID()}"
            offlineLocalLines.clear()
            _state.update {
                it.copy(
                    busy = false,
                    cartId = id,
                    cartLines = emptyList(),
                    pairingCodeDisplay = "",
                    scanSessionId = "",
                    lastInvoiceId = null,
                    lastBindMessage = null,
                    message = if (showMessage) {
                        "Offline cart $id — cash walk-in only"
                    } else {
                        it.message
                    },
                    error = null,
                )
            }
            return true
        }
        return try {
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
                    message = if (showMessage) {
                        "Open cart $id (no pairing required)"
                    } else {
                        it.message
                    },
                    error = null,
                )
            }
            startCartPolling(id)
            true
        } catch (e: Exception) {
            // Network failure → fall back to offline local cart when engine present.
            if (offlineEngine != null) {
                _state.update { it.copy(isOffline = true) }
                return ensureOpenCart(showMessage)
            }
            _state.update {
                it.copy(busy = false, error = e.message ?: "create cart failed")
            }
            false
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
                if (_state.value.isOffline) {
                    val engine = offlineEngine
                        ?: throw IllegalStateException("Offline catalog unavailable")
                    val wh = _state.value.warehouseId.trim()
                    val local = engine.searchLocal(wh, q)
                    val hits = local.map {
                        CatalogPartHit(
                            oemPartNumber = it.oemPartNumber,
                            categoryName = it.description,
                            saleableQty = it.saleableQty,
                        )
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            searchHits = hits,
                            message = if (hits.isEmpty()) {
                                "No offline hits for “$q” — pull snapshot when online"
                            } else {
                                "${hits.size} offline part(s)"
                            },
                        )
                    }
                    return@launch
                }
                val result = rpc.searchCatalog(_state.value.searchMode, q)
                val enriched = result.parts.map { hit ->
                    if (hit.saleableQty != null) {
                        hit
                    } else {
                        val qty = runCatching {
                            rpc.lookupSaleableQtyByOem(hit.oemPartNumber)
                        }.getOrNull()
                        hit.copy(saleableQty = qty)
                    }
                }
                _state.update {
                    it.copy(
                        busy = false,
                        searchHits = enriched,
                        message = if (enriched.isEmpty()) {
                            "No catalog hits for “$q”"
                        } else {
                            "${enriched.size} part(s) — tap Add to open cart"
                        },
                    )
                }
            } catch (e: Exception) {
                if (offlineEngine != null) {
                    _state.update { it.copy(isOffline = true) }
                    searchCatalog()
                    return@launch
                }
                _state.update {
                    it.copy(busy = false, error = e.message ?: "search failed")
                }
            }
        }
    }

    /** Resolve OEM via stock_items → [RpcNames.ADD_CART_LINE] (standalone; no session). */
    fun addPartFromCatalog(hit: CatalogPartHit) {
        val qty = _state.value.addQty.trim().toDoubleOrNull()
        viewModelScope.launch {
            if (_state.value.cartId.isBlank()) {
                _state.update { it.copy(busy = true, error = null) }
                if (!ensureOpenCart(showMessage = false)) {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = it.error ?: "Open cart required before adding lines",
                        )
                    }
                    return@launch
                }
            }
            addPartFromCatalogWithCart(hit, qty)
        }
    }

    /** EPC diagram / parts table → till cart (same path as search hit). */
    fun addOemToCart(oem: String) {
        addPartFromCatalog(CatalogPartHit(oemPartNumber = oem.trim()))
    }

    fun rpcForEpc(): RpcClient = rpc

    private suspend fun addPartFromCatalogWithCart(hit: CatalogPartHit, qty: Double?) {
        val cartId = _state.value.cartId.trim()
        when {
            cartId.isEmpty() -> {
                _state.update { it.copy(error = "Open cart required before adding lines") }
                return
            }
            qty == null || qty <= 0 -> {
                _state.update { it.copy(error = "Qty must be a positive number") }
                return
            }
        }
        _state.update { it.copy(busy = true, error = null, message = null) }
        try {
            if (_state.value.isOffline) {
                val engine = offlineEngine
                    ?: throw IllegalStateException("Offline catalog unavailable")
                val wh = _state.value.warehouseId.trim()
                val local = engine.searchLocal(wh, hit.oemPartNumber)
                    .firstOrNull {
                        it.oemPartNumber.equals(hit.oemPartNumber, ignoreCase = true)
                    }
                    ?: throw IllegalStateException(
                        "OEM ${hit.oemPartNumber} not in offline snapshot — pull when online",
                    )
                val lineId = UUID.randomUUID().toString()
                offlineLocalLines.add(
                    LocalCartLine(
                        id = lineId,
                        stockItemId = local.stockItemId,
                        oemPartNumber = local.oemPartNumber,
                        uomId = local.uomId,
                        qty = qty,
                        unitPrice = local.unitPrice,
                        lineTotal = local.unitPrice * qty,
                        isCoreCharge = false,
                    ),
                )
                if (local.coreCharge > 0) {
                    offlineLocalLines.add(
                        LocalCartLine(
                            id = UUID.randomUUID().toString(),
                            stockItemId = local.stockItemId,
                            oemPartNumber = local.oemPartNumber,
                            uomId = local.uomId,
                            qty = qty,
                            unitPrice = local.coreCharge,
                            lineTotal = local.coreCharge * qty,
                            isCoreCharge = true,
                        ),
                    )
                }
                _state.update {
                    it.copy(
                        busy = false,
                        cartLines = PosCartLineOps.toSummaries(offlineLocalLines),
                        message = "Offline added ${hit.oemPartNumber}",
                    )
                }
                return
            }
            val ref = rpc.lookupStockItemByOem(hit.oemPartNumber)
            val lineId = rpc.addCartLine(
                cartId = cartId,
                stockItemId = ref.stockItemId,
                uomId = ref.uomId,
                qty = qty,
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

    fun refreshBondedPrinters() {
        viewModelScope.launch {
            try {
                ensureBluetooth()
                val devices = printer.listBondedDevices()
                _state.update {
                    it.copy(
                        bondedPrinters = devices,
                        message = if (devices.isEmpty()) {
                            "No bonded BT printers — pair in system settings"
                        } else {
                            "${devices.size} bonded printer(s)"
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "list printers failed") }
            }
        }
    }

    fun selectBondedPrinter(device: BondedEscPosDevice) {
        _state.update { it.copy(printerMac = device.address) }
        connectPrinter()
    }

    fun onDiscountPercentChange(v: String) =
        _state.update { it.copy(discountPercent = v.filter { ch -> ch.isDigit() || ch == '.' }) }

    fun onOverrideUnitPriceChange(v: String) =
        _state.update { it.copy(overrideUnitPrice = v.filter { ch -> ch.isDigit() || ch == '.' }) }

    fun onManagerIdentifierChange(v: String) =
        _state.update { it.copy(managerIdentifier = v, error = null) }

    fun onManagerPasswordChange(v: String) =
        _state.update { it.copy(managerPassword = v, error = null) }

    fun onQuoteNotesChange(v: String) =
        _state.update { it.copy(quoteNotes = v) }

    fun onQuoteSendChannelChange(v: String) =
        _state.update { it.copy(quoteSendChannel = v) }

    fun onQuoteSendContactChange(v: String) =
        _state.update { it.copy(quoteSendContact = v) }

    fun onParkedCartIdChange(v: String) =
        _state.update { it.copy(parkedCartId = v) }

    fun dismissManagerPrompt() =
        _state.update {
            it.copy(
                managerPrompt = null,
                managerIdentifier = "",
                managerPassword = "",
                overrideLineId = "",
                overrideUnitPrice = "",
            )
        }

    fun requestDiscount() {
        if (_state.value.isOffline) {
            _state.update {
                it.copy(error = "Discount requires live manager auth (online only)")
            }
            return
        }
        if (_state.value.cartId.isBlank()) {
            _state.update { it.copy(error = "Open cart required") }
            return
        }
        _state.update { it.copy(managerPrompt = ManagerPrompt.Discount, error = null) }
    }

    fun requestPriceOverride(line: PosCartLineSummary) {
        if (_state.value.isOffline) {
            _state.update {
                it.copy(error = "Price override requires live manager auth (online only)")
            }
            return
        }
        if (line.isCoreCharge) {
            _state.update { it.copy(error = "Cannot override core-charge lines") }
            return
        }
        _state.update {
            it.copy(
                managerPrompt = ManagerPrompt.PriceOverride,
                overrideLineId = line.id,
                overrideUnitPrice = line.unitPrice.toString(),
                error = null,
            )
        }
    }

    fun requestVoidCart() {
        if (_state.value.isOffline) {
            // Local abandon is safe without manager token — clears offline cart only.
            offlineLocalLines.clear()
            cartPollJob?.cancel()
            _state.update {
                it.copy(
                    cartId = "",
                    cartLines = emptyList(),
                    message = "Offline cart discarded (not a server void)",
                    error = null,
                )
            }
            return
        }
        if (_state.value.cartId.isBlank()) {
            _state.update { it.copy(error = "Open cart required") }
            return
        }
        _state.update { it.copy(managerPrompt = ManagerPrompt.VoidCart, error = null) }
    }

    fun requestRefund() {
        if (_state.value.isOffline) {
            _state.update {
                it.copy(error = "Refunds require live manager auth + finance pipeline (online only)")
            }
            return
        }
        if (_state.value.lastInvoiceId.isNullOrBlank()) {
            _state.update { it.copy(error = "Checkout an invoice first to refund") }
            return
        }
        _state.update { it.copy(managerPrompt = ManagerPrompt.Refund, error = null) }
    }

    fun confirmManagerAction() {
        val prompt = _state.value.managerPrompt ?: return
        val identifier = _state.value.managerIdentifier.trim()
        val password = _state.value.managerPassword
        if (identifier.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Manager emp#/email/phone + password required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                when (prompt) {
                    ManagerPrompt.Discount -> {
                        val pct = _state.value.discountPercent.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Invalid discount %")
                        withManagerApproval(identifier, password) {
                            rpc.applyPosCartDiscount(_state.value.cartId, pct)
                        }
                        refreshCartLines(_state.value.cartId)
                        _state.update {
                            it.copy(
                                busy = false,
                                managerPrompt = null,
                                managerIdentifier = "",
                                managerPassword = "",
                                message = "Discount ${pct}% applied (manager approved)",
                            )
                        }
                    }
                    ManagerPrompt.PriceOverride -> {
                        val lineId = _state.value.overrideLineId
                        val unit = _state.value.overrideUnitPrice.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Invalid unit price")
                        withManagerApproval(identifier, password) {
                            rpc.applyPosLinePriceOverride(lineId, unit)
                        }
                        refreshCartLines(_state.value.cartId)
                        _state.update {
                            it.copy(
                                busy = false,
                                managerPrompt = null,
                                managerIdentifier = "",
                                managerPassword = "",
                                overrideLineId = "",
                                overrideUnitPrice = "",
                                message = "Price override applied (manager approved)",
                            )
                        }
                    }
                    ManagerPrompt.VoidCart -> {
                        val cartId = _state.value.cartId
                        withManagerApproval(identifier, password) {
                            rpc.voidPosCart(cartId, "void from tablet POS")
                        }
                        cartPollJob?.cancel()
                        _state.update {
                            it.copy(
                                busy = false,
                                cartId = "",
                                cartLines = emptyList(),
                                managerPrompt = null,
                                managerIdentifier = "",
                                managerPassword = "",
                                message = "Cart voided (manager approved)",
                            )
                        }
                    }
                    ManagerPrompt.Refund -> {
                        val invoiceId = _state.value.lastInvoiceId!!
                        val refundId = withManagerApproval(identifier, password) {
                            rpc.postPosRefund(invoiceId, "POS counter refund")
                        }
                        _state.update {
                            it.copy(
                                busy = false,
                                managerPrompt = null,
                                managerIdentifier = "",
                                managerPassword = "",
                                message = "${RpcNames.POST_POS_REFUND} → $refundId " +
                                    "(via ${RpcNames.POST_FINANCE_REFUND})",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "manager action failed")
                }
            }
        }
    }

    fun parkCart() {
        val cartId = _state.value.cartId.trim()
        if (cartId.isEmpty()) {
            _state.update { it.copy(error = "Open cart required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.parkPosCart(cartId)
                cartPollJob?.cancel()
                _state.update {
                    it.copy(
                        busy = false,
                        cartId = "",
                        cartLines = emptyList(),
                        parkedCartId = cartId,
                        message = "Cart parked — resume with id $cartId",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "park failed") }
            }
        }
    }

    fun resumeParkedCart() {
        val cartId = _state.value.parkedCartId.trim()
        if (cartId.isEmpty()) {
            _state.update { it.copy(error = "Parked cart UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                rpc.resumePosCart(cartId)
                refreshCartLines(cartId)
                startCartPolling(cartId)
                _state.update {
                    it.copy(
                        busy = false,
                        cartId = cartId,
                        message = "Resumed cart $cartId",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "resume failed") }
            }
        }
    }

    fun createQuotation() {
        val cartId = _state.value.cartId.trim()
        if (cartId.isEmpty() || _state.value.cartLines.isEmpty()) {
            _state.update { it.copy(error = "Open cart with lines required for quotation") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val quoteId = rpc.createPosQuotationFromCart(
                    cartId = cartId,
                    notes = _state.value.quoteNotes.trim().ifBlank { null },
                )
                val quotes = rpc.listPosQuotations()
                _state.update {
                    it.copy(
                        busy = false,
                        quotations = quotes,
                        showQuotes = true,
                        message = "Quotation $quoteId issued (no tender / no ledger)",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "quote create failed") }
            }
        }
    }

    fun toggleQuotes() {
        viewModelScope.launch {
            if (!_state.value.showQuotes) {
                try {
                    val quotes = rpc.listPosQuotations()
                    _state.update { it.copy(showQuotes = true, quotations = quotes) }
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message ?: "list quotes failed") }
                }
            } else {
                _state.update { it.copy(showQuotes = false) }
            }
        }
    }

    fun sendQuotation(quotationId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val channel = _state.value.quoteSendChannel
                rpc.sendPosQuotation(
                    quotationId = quotationId,
                    channel = channel,
                    contact = _state.value.quoteSendContact.trim().ifBlank { null },
                )
                if (channel == "print" && printer.isConnected()) {
                    printer.printReceiptLines(
                        listOf(
                            EscPosReceiptLine("GTR Auto QUOTATION", emphasis = true),
                            EscPosReceiptLine("Quote: $quotationId"),
                            EscPosReceiptLine("Currency: ${_state.value.currency.rpcValue}"),
                            EscPosReceiptLine("Thank you"),
                        ),
                    )
                }
                val quotes = rpc.listPosQuotations()
                _state.update {
                    it.copy(
                        busy = false,
                        quotations = quotes,
                        message = "Quotation sent via $channel",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "send quote failed") }
            }
        }
    }

    fun convertQuotation(quotationId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val cartId = rpc.convertPosQuotationToCart(quotationId)
                refreshCartLines(cartId)
                startCartPolling(cartId)
                val quotes = rpc.listPosQuotations()
                _state.update {
                    it.copy(
                        busy = false,
                        cartId = cartId,
                        quotations = quotes,
                        showQuotes = false,
                        message = "Quote converted → cart $cartId — ready to checkout",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "convert quote failed")
                }
            }
        }
    }

    fun bumpLineQty(line: PosCartLineSummary, delta: Double) {
        if (line.isCoreCharge) return
        if (_state.value.isOffline) {
            PosCartLineOps.applyOfflineQtyDelta(offlineLocalLines, line.id, line.stockItemId, delta)
            _state.update { it.copy(cartLines = PosCartLineOps.toSummaries(offlineLocalLines)) }
            return
        }
        val next = (line.qty + delta).coerceAtLeast(0.0)
        viewModelScope.launch {
            try {
                if (next <= 0) {
                    rpc.deletePosCartLine(line.id)
                } else {
                    rpc.setPosCartLineQty(line.id, next, line.unitPrice)
                }
                refreshCartLines(_state.value.cartId)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "qty update failed") }
            }
        }
    }

    private suspend fun <T> withManagerApproval(
        identifier: String,
        password: String,
        block: suspend () -> T,
    ): T {
        val live = rpc as? SupabaseRpcClient
        return if (live != null) {
            live.withManagerApproval(identifier, password, block)
        } else {
            block()
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
                if (_state.value.isOffline) {
                    val engine = offlineEngine
                        ?: throw IllegalStateException("Offline engine unavailable")
                    val nonCash = _state.value.tenderLines.any {
                        it.tender.isNotBlank() &&
                            !it.tender.equals("cash", ignoreCase = true) &&
                            (it.amount.toDoubleOrNull() ?: 0.0) > 0
                    }
                    if (nonCash) {
                        throw IllegalStateException(
                            "Offline checkout is cash-only (EcoCash/Paynow require live rails)",
                        )
                    }
                    if (_state.value.customerId.isNotBlank()) {
                        throw IllegalStateException(
                            "Named credit customers are online-only — clear customer for walk-in cash",
                        )
                    }
                    val clientSaleId = engine.queueCashSale(
                        warehouseId = _state.value.warehouseId.trim(),
                        currency = _state.value.currency,
                        exchangeRate = 1.0,
                        lines = offlineLocalLines.toList(),
                        receiptEmail = _state.value.receiptEmail.trim().ifBlank { null },
                        receiptWhatsapp = _state.value.receiptWhatsapp.trim().ifBlank { null },
                        receiptPhone = _state.value.receiptWhatsapp.trim().ifBlank { null },
                    )
                    val total = offlineLocalLines.sumOf { it.lineTotal }
                    offlineLocalLines.clear()
                    refreshOfflineBadge()
                    var msg = "Offline sale queued $clientSaleId — will sync when online"
                    if (printer.isConnected()) {
                        try {
                            printer.printReceiptLines(
                                listOf(
                                    EscPosReceiptLine("GTR Auto POS (OFFLINE)", emphasis = true),
                                    EscPosReceiptLine("Queued: $clientSaleId"),
                                    EscPosReceiptLine("Currency: ${_state.value.currency.rpcValue}"),
                                    EscPosReceiptLine("Total: ${"%.2f".format(total)}"),
                                    EscPosReceiptLine("Sync when online"),
                                ),
                            )
                            msg += " · provisional receipt printed"
                        } catch (pe: Exception) {
                            msg += " · print skipped (${pe.message})"
                        }
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            cartId = "",
                            cartLines = emptyList(),
                            tenderLines = listOf(PosTenderDraft()),
                            lastInvoiceId = clientSaleId,
                            lastInvoiceTotal = total,
                            lastBindMessage = "Offline walk-in — pending sync",
                            message = msg,
                        )
                    }
                    return@launch
                }
                val tenders = _state.value.tenderLines.mapNotNull { row ->
                    val amt = row.amount.toDoubleOrNull() ?: return@mapNotNull null
                    if (amt <= 0) return@mapNotNull null
                    PosTenderLine(
                        tender = row.tender,
                        amount = amt,
                        currency = _state.value.currency.rpcValue,
                    )
                }
                val result = if (tenders.isNotEmpty()) {
                    rpc.checkoutPosCartWithTenders(
                        cartId = cartId,
                        tenders = tenders,
                        receiptEmail = _state.value.receiptEmail.trim().ifBlank { null },
                        receiptWhatsappE164 = _state.value.receiptWhatsapp.trim().ifBlank { null },
                        receiptPhoneE164 = _state.value.receiptWhatsapp.trim().ifBlank { null },
                    )
                } else {
                    rpc.checkoutPosCart(
                        cartId = cartId,
                        receiptEmail = _state.value.receiptEmail.trim().ifBlank { null },
                        receiptWhatsappE164 = _state.value.receiptWhatsapp.trim().ifBlank { null },
                        receiptPhoneE164 = _state.value.receiptWhatsapp.trim().ifBlank { null },
                    )
                }
                cartPollJob?.cancel()
                var msg = if (tenders.isNotEmpty()) {
                    "${RpcNames.CHECKOUT_POS_CART_WITH_TENDERS} → ${result.invoiceId}"
                } else {
                    "${RpcNames.CHECKOUT_POS_CART} → ${result.invoiceId}"
                }
                msg += " · ${result.bindMessage}"
                val total = _state.value.cartLines.sumOf { it.lineTotal }
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
                        tenderLines = listOf(PosTenderDraft()),
                        lastInvoiceId = result.invoiceId,
                        lastInvoiceTotal = total,
                        lastBindMessage = result.bindMessage,
                        ecocashMsisdn = _state.value.receiptWhatsapp.ifBlank {
                            it.ecocashMsisdn
                        },
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
            offlineEngine: OfflinePosSyncEngine? = null,
            onlineFlow: Flow<Boolean>? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PosViewModel(rpc, qr, printer, offlineEngine, onlineFlow) as T
            }
    }
}
