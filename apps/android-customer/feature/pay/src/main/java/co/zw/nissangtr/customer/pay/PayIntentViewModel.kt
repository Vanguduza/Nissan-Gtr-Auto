package co.zw.nissangtr.customer.pay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.customer.rpc.CheckoutDisplay
import co.zw.nissangtr.customer.rpc.CheckoutDisplayBuilder
import co.zw.nissangtr.customer.rpc.CheckoutPayMethod
import co.zw.nissangtr.customer.rpc.ContipayMethod
import co.zw.nissangtr.customer.rpc.CurrencyCode
import co.zw.nissangtr.customer.rpc.InvoiceSummary
import co.zw.nissangtr.customer.rpc.MoneyDualRead
import co.zw.nissangtr.customer.rpc.PaynowMethod
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EcoCashPayerMode(val rpcValue: String) {
    Saved("saved"),
    Other("other"),
}

data class PayUiState(
    val invoices: List<InvoiceSummary> = emptyList(),
    val invoiceId: String = "",
    val ecocashMode: EcoCashPayerMode = EcoCashPayerMode.Saved,
    val ecocashMsisdn: String = "",
    val profilePhone: String? = null,
    /** Ops daily ZiG per USD; null when unavailable. */
    val zigRate: Double? = null,
    /** `daily_exchange_rates.id` when ops row present. */
    val fxRateId: String? = null,
    val lastIntentId: String? = null,
    val lastProvider: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class PayIntentViewModel(
    private val rpc: RpcClient,
) : ViewModel() {
    private val _state = MutableStateFlow(PayUiState())
    val state: StateFlow<PayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onInvoiceIdChange(v: String) = _state.update { it.copy(invoiceId = v, error = null) }

    fun onEcocashMsisdnChange(v: String) = _state.update { it.copy(ecocashMsisdn = v, error = null) }

    fun onEcocashModeChange(mode: EcoCashPayerMode) =
        _state.update { it.copy(ecocashMode = mode, error = null) }

    fun selectInvoice(id: String) = _state.update { it.copy(invoiceId = id, error = null) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = rpc.listOwnInvoices()
                val rate = rpc.fetchZigExchangeRate()
                val fxId = rpc.fetchZigExchangeRateId()
                val profilePhone = rpc.loadOwnCustomer()?.phoneE164
                val firstOpen = list.firstOrNull { it.total > it.amountPaid }
                _state.update {
                    it.copy(
                        busy = false,
                        invoices = list,
                        invoiceId = it.invoiceId.ifBlank { firstOpen?.id.orEmpty() },
                        zigRate = rate.takeIf { r -> r.isFinite() && r > 0.0 },
                        fxRateId = fxId,
                        profilePhone = profilePhone,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "list failed") }
            }
        }
    }

    /** Selected invoice open balance in USD minor (browse currency). */
    fun selectedOpenUsdMinor(): Long? {
        val inv = selectedInvoice() ?: return null
        val openMajor = (inv.total - inv.amountPaid).coerceAtLeast(0.0)
        return MoneyDualRead.toAmountMinor(openMajor, CurrencyCode.USD)
    }

    fun selectedInvoice(): InvoiceSummary? {
        val id = _state.value.invoiceId.trim()
        if (id.isEmpty()) return null
        return _state.value.invoices.firstOrNull { it.id == id }
    }

    /**
     * D-57 checkout display for pay step.
     * EcoCash → ZiG payable from MoneyMinor + fxRateId; fail-closed → null when rate missing.
     */
    fun checkoutDisplay(payMethod: CheckoutPayMethod): CheckoutDisplay? {
        val usdMinor = selectedOpenUsdMinor() ?: return null
        val s = _state.value
        return try {
            CheckoutDisplayBuilder.build(
                usdMinor = usdMinor,
                payMethod = payMethod,
                zigRatePerUsd = s.zigRate,
                fxRateId = if (payMethod == CheckoutPayMethod.ECOCASH) s.fxRateId else null,
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun createContipay() {
        val invoiceId = _state.value.invoiceId.trim()
        if (invoiceId.isEmpty()) {
            _state.update { it.copy(error = "Invoice UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val result = rpc.createCustomerContipayIntent(
                    salesInvoiceId = invoiceId,
                    method = ContipayMethod.ECOCASH,
                    metadataJson = """{"channel":"storefront","sales_invoice_id":"$invoiceId"}""",
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastIntentId = result.intentId,
                        lastProvider = result.provider,
                        message = "${RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT} → ${result.intentId}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "contipay failed") }
            }
        }
    }

    fun createPaynow() {
        val invoiceId = _state.value.invoiceId.trim()
        if (invoiceId.isEmpty()) {
            _state.update { it.copy(error = "Invoice UUID required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val result = rpc.createCustomerPaynowIntent(
                    salesInvoiceId = invoiceId,
                    method = PaynowMethod.ECOCASH,
                    metadataJson = """{"channel":"storefront","sales_invoice_id":"$invoiceId"}""",
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastIntentId = result.intentId,
                        lastProvider = result.provider,
                        message = "${RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT} → ${result.intentId}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "paynow failed") }
            }
        }
    }

    fun createEcocash() {
        val invoiceId = _state.value.invoiceId.trim()
        val mode = _state.value.ecocashMode
        val msisdn = when (mode) {
            EcoCashPayerMode.Saved -> ""
            EcoCashPayerMode.Other -> _state.value.ecocashMsisdn.trim()
        }
        if (invoiceId.isEmpty()) {
            _state.update { it.copy(error = "Invoice UUID required") }
            return
        }
        if (mode == EcoCashPayerMode.Saved && _state.value.profilePhone.isNullOrBlank()) {
            _state.update {
                it.copy(error = "No profile phone on file — add one in Edit profile, or choose Other number.")
            }
            return
        }
        if (mode == EcoCashPayerMode.Other && msisdn.isEmpty()) {
            _state.update { it.copy(error = "Enter EcoCash number for Other mode") }
            return
        }
        // D-57 fail-closed: EcoCash ZiG wallet needs ops daily rate
        val display = checkoutDisplay(CheckoutPayMethod.ECOCASH)
        if (display == null) {
            _state.update {
                it.copy(
                    error = "Daily ZiG rate required for EcoCash. Try again later or use ContiPay/Paynow in USD.",
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            try {
                val fxMeta = display.fxRateId?.let { """"fx_rate_id":"$it",""" }.orEmpty()
                val result = rpc.createCustomerEcocashIntent(
                    salesInvoiceId = invoiceId,
                    payerMsisdn = msisdn,
                    payerMode = mode.rpcValue,
                    metadataJson =
                        """{"channel":"android_customer","sales_invoice_id":"$invoiceId",$fxMeta"settlement_amount_minor":${display.payable.amountMinor},"settlement_currency":"ZIG"}""",
                )
                _state.update {
                    it.copy(
                        busy = false,
                        lastIntentId = result.intentId,
                        lastProvider = result.provider,
                        message = "${RpcNames.CREATE_CUSTOMER_ECOCASH_INTENT} → ${result.intentId} — approve PIN on EcoCash handset",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "ecocash failed") }
            }
        }
    }

    companion object {
        fun factory(rpc: RpcClient): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PayIntentViewModel(rpc) as T
            }
    }
}
