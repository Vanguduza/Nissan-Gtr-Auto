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
    val currency: CurrencyCode = CurrencyCode.USD,
    val fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
    val zigRate: Double = 1.0,
    val cart: CartSummary? = null,
    val addresses: List<co.zw.nissangtr.customer.rpc.CustomerAddress> = emptyList(),
    val selectedAddressId: String? = null,
    val lastInvoiceId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Cart / checkout ViewModel — mirrors web `CartCheckout`.
 * Lines come from catalog add-to-cart (no UUID stubs in primary UX).
 */
class CartViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onCurrencyChange(v: CurrencyCode) = _state.update { it.copy(currency = v) }
    fun onFulfillmentChange(v: FulfillmentMode) = _state.update { it.copy(fulfillmentMode = v) }
    fun onAddressSelect(id: String) = _state.update { it.copy(selectedAddressId = id) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val rate = rpc.fetchZigExchangeRate()
                val cart = rpc.getOpenCart()
                val addresses = runCatching { rpc.listOwnAddresses() }.getOrDefault(emptyList())
                val defaultId = addresses.firstOrNull { it.isDefault }?.id
                    ?: addresses.firstOrNull()?.id
                _state.update {
                    it.copy(
                        busy = false,
                        zigRate = rate,
                        cart = cart,
                        addresses = addresses,
                        selectedAddressId = it.selectedAddressId ?: defaultId,
                        currency = cart?.currency ?: it.currency,
                        fulfillmentMode = cart?.fulfillmentMode ?: it.fulfillmentMode,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "refresh failed") }
            }
        }
    }

    fun ensureCart() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val s = _state.value
                val rate = if (s.currency == CurrencyCode.ZIG) {
                    rpc.fetchZigExchangeRate().also { r ->
                        _state.update { it.copy(zigRate = r) }
                    }
                } else {
                    1.0
                }
                val cart = rpc.ensureOpenCart(
                    currency = s.currency,
                    fulfillmentMode = s.fulfillmentMode,
                    exchangeRate = rate,
                )
                _state.update {
                    it.copy(
                        busy = false,
                        cart = cart,
                        message = "Cart ready · ${fulfillmentLabel(cart.fulfillmentMode)}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "ensure cart failed") }
            }
        }
    }

    fun checkout(onInvoice: (String) -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                // Sync fulfillment / currency onto open cart before checkout.
                val s = _state.value
                val rate = if (s.currency == CurrencyCode.ZIG) {
                    rpc.fetchZigExchangeRate().also { r ->
                        _state.update { it.copy(zigRate = r) }
                    }
                } else {
                    1.0
                }
                var cart = rpc.ensureOpenCart(
                    currency = s.currency,
                    fulfillmentMode = s.fulfillmentMode,
                    exchangeRate = rate,
                )
                if (cart.lines.isEmpty()) {
                    cart = rpc.getOpenCart() ?: cart
                }
                if (cart.lines.isEmpty()) {
                    _state.update { it.copy(busy = false, error = "Add a part before checkout.") }
                    return@launch
                }
                val mode = cart.fulfillmentMode
                if (mode == FulfillmentMode.DISPATCH) {
                    val addrId = _state.value.selectedAddressId
                    if (addrId.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                busy = false,
                                error = "Select a delivery address for Nationwide dispatch.",
                            )
                        }
                        return@launch
                    }
                }
                val invoiceId = rpc.checkoutCustomerCart(cart.id)
                _state.update {
                    it.copy(
                        busy = false,
                        cart = null,
                        lastInvoiceId = invoiceId,
                        message = "Order placed · invoice $invoiceId. Opening secure payment…",
                    )
                }
                onInvoice(invoiceId)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "checkout failed") }
            }
        }
    }

    companion object {
        fun fulfillmentLabel(mode: FulfillmentMode): String =
            when (mode) {
                FulfillmentMode.IMMEDIATE -> "Click & collect"
                FulfillmentMode.DISPATCH -> "Nationwide dispatch"
            }

        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CartViewModel(rpc) as T
            }
    }
}
