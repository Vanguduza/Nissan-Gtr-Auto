package co.zw.nissangtr.management.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val lastLineId: String? = null,
    val lastInvoiceId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Thin POS scaffold: create cart → add stock line (typed UUIDs) → checkout.
 * No HTML5 / browser QR — Bridge-First `add_cart_line_from_qr` is out of scope here.
 */
class PosViewModel(
    private val rpc: RpcClient,
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
                _state.update {
                    it.copy(
                        busy = false,
                        lastInvoiceId = invoiceId,
                        message = "${RpcNames.CHECKOUT_POS_CART} → invoice $invoiceId",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "checkout failed")
                }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PosViewModel(rpc) as T
            }
    }
}
