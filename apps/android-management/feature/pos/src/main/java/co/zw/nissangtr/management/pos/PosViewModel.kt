package co.zw.nissangtr.management.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.bridges.escpos.BluetoothPermissionStatus
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosReceiptLine
import co.zw.nissangtr.bridges.qr.CameraPermissionStatus
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.FulfillmentMode
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PosUiState(
    val warehouseId: String = "",
    val customerId: String = "",
    val currency: CurrencyCode = CurrencyCode.USD,
    val fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
    val cartId: String = "",
    val stockItemId: String = "",
    val uomId: String = "",
    val qty: String = "1",
    val printerMac: String = "",
    val printerConnected: Boolean = false,
    val lastLineId: String? = null,
    val lastInvoiceId: String? = null,
    val lastQrPayload: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * POS scaffold: create cart → add line (typed or Bridge-First QR) → checkout.
 * Optional best-effort ESC/POS receipt when printer is paired/connected.
 * No HTML5 / browser QR.
 */
class PosViewModel(
    private val rpc: RpcClient,
    private val qr: QrScannerBridge,
    private val printer: EscPosPrinterBridge,
) : ViewModel() {
    private val _state = MutableStateFlow(PosUiState())
    val state: StateFlow<PosUiState> = _state.asStateFlow()

    fun onWarehouseIdChange(v: String) =
        _state.update { it.copy(warehouseId = v, error = null) }

    fun onCustomerIdChange(v: String) =
        _state.update { it.copy(customerId = v) }

    fun onCurrencyChange(v: CurrencyCode) =
        _state.update { it.copy(currency = v, error = null) }

    fun onFulfillmentModeChange(v: FulfillmentMode) =
        _state.update { it.copy(fulfillmentMode = v) }

    fun onCartIdChange(v: String) =
        _state.update { it.copy(cartId = v, error = null) }

    fun onStockItemIdChange(v: String) =
        _state.update { it.copy(stockItemId = v, error = null) }

    fun onUomIdChange(v: String) =
        _state.update { it.copy(uomId = v, error = null) }

    fun onQtyChange(v: String) =
        _state.update { it.copy(qty = v) }

    fun onPrinterMacChange(v: String) =
        _state.update { it.copy(printerMac = v) }

    fun createCart() {
        val warehouseId = _state.value.warehouseId.trim()
        if (warehouseId.isEmpty()) {
            _state.update { it.copy(error = "Warehouse UUID required") }
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
                        message = "${RpcNames.CREATE_POS_CART} (${it.currency.rpcValue}) → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "create cart failed")
                }
            }
        }
    }

    fun addLine() {
        val cartId = _state.value.cartId.trim()
        val stockItemId = _state.value.stockItemId.trim()
        val uomId = _state.value.uomId.trim()
        val qty = _state.value.qty.trim().toDoubleOrNull()
        when {
            cartId.isEmpty() -> {
                _state.update { it.copy(error = "Cart UUID required") }
                return
            }
            stockItemId.isEmpty() -> {
                _state.update { it.copy(error = "Stock item UUID required (typed OEM path)") }
                return
            }
            uomId.isEmpty() -> {
                _state.update { it.copy(error = "UOM UUID required") }
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
                val lineId = rpc.addCartLine(
                    cartId = cartId,
                    stockItemId = stockItemId,
                    uomId = uomId,
                    qty = qty!!,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastLineId = lineId,
                        message = "${RpcNames.ADD_CART_LINE} → $lineId",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "add line failed")
                }
            }
        }
    }

    /** CameraX scan → [RpcNames.ADD_CART_LINE_FROM_QR] with full raw payload. */
    fun scanQrAddLine() {
        val cartId = _state.value.cartId.trim()
        val qty = _state.value.qty.trim().toDoubleOrNull() ?: 1.0
        if (cartId.isEmpty()) {
            _state.update { it.copy(error = "Cart UUID required before QR scan") }
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
                        lastLineId = lineId,
                        lastQrPayload = scan.rawValue,
                        message = "${RpcNames.ADD_CART_LINE_FROM_QR} → $lineId",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "QR add line failed")
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
                if (printer is co.zw.nissangtr.bridges.escpos.BluetoothEscPosPrinterBridge) {
                    printer.setPrinterAddress(mac)
                }
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
            _state.update { it.copy(error = "Cart UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val invoiceId = rpc.checkoutPosCart(cartId)
                var msg = "${RpcNames.CHECKOUT_POS_CART} → invoice $invoiceId"
                // Best-effort receipt — never fails checkout if print fails.
                if (printer.isConnected()) {
                    try {
                        printer.printReceiptLines(
                            listOf(
                                EscPosReceiptLine("GTR Auto POS", emphasis = true),
                                EscPosReceiptLine("Invoice: $invoiceId"),
                                EscPosReceiptLine("Currency: ${_state.value.currency.rpcValue}"),
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
                        lastInvoiceId = invoiceId,
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

    companion object {
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
