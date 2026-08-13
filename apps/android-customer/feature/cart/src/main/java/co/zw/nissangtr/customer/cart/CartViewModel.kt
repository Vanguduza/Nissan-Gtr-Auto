package co.zw.nissangtr.customer.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.CartSummary
import co.zw.nissangtr.customer.rpc.CheckoutDisplay
import co.zw.nissangtr.customer.rpc.CheckoutDisplayBuilder
import co.zw.nissangtr.customer.rpc.CheckoutPayMethod
import co.zw.nissangtr.customer.rpc.CurrencyCode
import co.zw.nissangtr.customer.rpc.FulfillmentMode
import co.zw.nissangtr.customer.rpc.MoneyDualRead
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.displaySubtotal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartUiState(
    /** D-57 settle currency at pay step — cart browse remains USD. */
    val settleCurrency: CurrencyCode = CurrencyCode.USD,
    val fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
    val zigRate: Double? = null,
    /** `daily_exchange_rates.id` for ops rate; null when fallback-only. */
    val fxRateId: String? = null,
    val cart: CartSummary? = null,
    val addresses: List<co.zw.nissangtr.customer.rpc.CustomerAddress> = emptyList(),
    val selectedAddressId: String? = null,
    val lastInvoiceId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Cart / checkout ViewModel — mirrors web `CartCheckout` D-57 habits:
 * browse/cart USD; ZiG only at settle with ops rate + fxRateId.
 */
class CartViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onSettleCurrencyChange(v: CurrencyCode) =
        _state.update { it.copy(settleCurrency = v, error = null) }

    fun onFulfillmentChange(v: FulfillmentMode) = _state.update { it.copy(fulfillmentMode = v) }
    fun onAddressSelect(id: String) = _state.update { it.copy(selectedAddressId = id) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val rate = rpc.fetchZigExchangeRate()
                val fxId = rpc.fetchZigExchangeRateId()
                val cart = rpc.getOpenCart()
                val addresses = runCatching { rpc.listOwnAddresses() }.getOrDefault(emptyList())
                val defaultId = addresses.firstOrNull { it.isDefault }?.id
                    ?: addresses.firstOrNull()?.id
                _state.update {
                    it.copy(
                        busy = false,
                        zigRate = rate.takeIf { r -> r.isFinite() && r > 0.0 },
                        fxRateId = fxId,
                        cart = cart,
                        addresses = addresses,
                        selectedAddressId = it.selectedAddressId ?: defaultId,
                        fulfillmentMode = cart?.fulfillmentMode ?: it.fulfillmentMode,
                        // D-57: do not mirror cart.currency into settle — browse is USD
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
                // D-57: cart SoR stays USD; settle currency is pay-step only
                val cart = rpc.ensureOpenCart(
                    currency = CurrencyCode.USD,
                    fulfillmentMode = s.fulfillmentMode,
                    exchangeRate = 1.0,
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

    /**
     * D-57 checkout display for current cart USD total.
     * Null when ZiG settle requested but rate missing (fail-closed).
     */
    fun checkoutDisplayOrNull(): CheckoutDisplay? {
        val s = _state.value
        val cart = s.cart ?: return null
        val usdMinor = MoneyDualRead.toAmountMinor(cart.displaySubtotal(), CurrencyCode.USD)
        val zigPay = s.settleCurrency == CurrencyCode.ZIG
        return try {
            CheckoutDisplayBuilder.build(
                usdMinor = usdMinor,
                payMethod = if (zigPay) CheckoutPayMethod.ECOCASH else CheckoutPayMethod.CASH,
                zigRatePerUsd = s.zigRate,
                fxRateId = if (zigPay) s.fxRateId else null,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun checkout(onInvoice: (String) -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val s = _state.value
                // D-57 fail-closed: ZiG settle needs ops daily rate
                if (s.settleCurrency == CurrencyCode.ZIG) {
                    val rate = s.zigRate
                    if (rate == null || !rate.isFinite() || rate <= 0.0) {
                        _state.update {
                            it.copy(
                                busy = false,
                                error = "Daily ZiG rate required to settle in ZiG. Try again later or pay in USD.",
                            )
                        }
                        return@launch
                    }
                }
                // D-57: always USD cart; settle currency is display/pay metadata only
                var cart = rpc.ensureOpenCart(
                    currency = CurrencyCode.USD,
                    fulfillmentMode = s.fulfillmentMode,
                    exchangeRate = 1.0,
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
