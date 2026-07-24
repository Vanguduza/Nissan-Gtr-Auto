package co.zw.nissangtr.customer.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.CartSummary
import co.zw.nissangtr.customer.rpc.CurrencyCode
import co.zw.nissangtr.customer.rpc.FulfillmentMode
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartUiState(
    val warehouseId: String = "00000000-0000-4000-8000-0000000000w1",
    val stockItemId: String = "00000000-0000-4000-8000-0000000000s1",
    val uomId: String = "00000000-0000-4000-8000-0000000000u1",
    val qty: String = "1",
    val currency: CurrencyCode = CurrencyCode.USD,
    val fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
    val cart: CartSummary? = null,
    val lastInvoiceId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class CartViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onWarehouseIdChange(v: String) = _state.update { it.copy(warehouseId = v, error = null) }
    fun onStockItemIdChange(v: String) = _state.update { it.copy(stockItemId = v, error = null) }
    fun onUomIdChange(v: String) = _state.update { it.copy(uomId = v, error = null) }
    fun onQtyChange(v: String) = _state.update { it.copy(qty = v, error = null) }
    fun onCurrencyChange(v: CurrencyCode) = _state.update { it.copy(currency = v) }
    fun onFulfillmentChange(v: FulfillmentMode) = _state.update { it.copy(fulfillmentMode = v) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val cart = rpc.getOpenCart()
                _state.update { it.copy(busy = false, cart = cart) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "refresh failed") }
            }
        }
    }

    fun createCart() {
        val warehouseId = _state.value.warehouseId.trim()
        if (warehouseId.isEmpty()) {
            _state.update { it.copy(error = "Warehouse UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val s = _state.value
                val rate = if (s.currency == CurrencyCode.ZIG) 1.0 else 1.0
                val id = rpc.createCustomerCart(
                    warehouseId = warehouseId,
                    currency = s.currency,
                    fulfillmentMode = s.fulfillmentMode,
                    exchangeRate = rate,
                )
                val cart = rpc.getOpenCart()
                _state.update {
                    it.copy(
                        busy = false,
                        cart = cart,
                        message = "${RpcNames.CREATE_CUSTOMER_CART} → $id",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "create failed") }
            }
        }
    }

    fun addLine() {
        val cartId = _state.value.cart?.id
        if (cartId.isNullOrBlank()) {
            _state.update { it.copy(error = "Create an open cart first") }
            return
        }
        val qty = _state.value.qty.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _state.update { it.copy(error = "Qty must be > 0") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val s = _state.value
                val lineId = rpc.addCustomerCartLine(
                    cartId = cartId,
                    stockItemId = s.stockItemId.trim(),
                    uomId = s.uomId.trim(),
                    qty = qty,
                )
                val cart = rpc.getOpenCart()
                _state.update {
                    it.copy(
                        busy = false,
                        cart = cart,
                        message = "${RpcNames.ADD_CUSTOMER_CART_LINE} → $lineId",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "add line failed") }
            }
        }
    }

    fun checkout() {
        val cartId = _state.value.cart?.id
        if (cartId.isNullOrBlank()) {
            _state.update { it.copy(error = "No open cart") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val invoiceId = rpc.checkoutCustomerCart(cartId)
                _state.update {
                    it.copy(
                        busy = false,
                        cart = null,
                        lastInvoiceId = invoiceId,
                        message = "${RpcNames.CHECKOUT_CUSTOMER_CART} → invoice $invoiceId",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "checkout failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CartViewModel(rpc) as T
            }
    }
}
