package co.zw.nissangtr.pos.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Matches `public.payment_tender` (+ ecocash / paynow). */
enum class TenderMode(val rpcValue: String) {
    CASH("cash"),
    BANK("bank"),
    STORE_CREDIT("store_credit"),
    ECOCASH("ecocash"),
    PAYNOW("paynow"),
    CONTIPAY("contipay"),
    ;

    val isLiveRail: Boolean
        get() = this == ECOCASH || this == PAYNOW || this == CONTIPAY

    val requiresConnection: Boolean
        get() = this != CASH

    companion object {
        fun fromRpc(value: String): TenderMode =
            entries.first { it.rpcValue.equals(value, ignoreCase = true) }
    }
}

/**
 * One tender line for `checkout_pos_cart_with_tenders` / `settle_invoice_tenders`.
 * [amount] is applied only (major units, 2 dp) — never cash change.
 */
@Serializable
data class TenderRpcLine(
    val tender: String,
    val amount: String,
    val currency: String,
    @SerialName("exchange_rate") val exchangeRate: String? = null,
)

@Serializable
data class CheckoutReceiptContacts(
    val email: String? = null,
    val whatsappE164: String? = null,
    val phoneE164: String? = null,
)

@Serializable
data class CheckoutResult(
    val invoiceId: String,
    /** `posted` | `on_hold` | … */
    val status: String,
    /**
     * False when credit-hold / on_hold — invoice exists but is **not** treated as paid
     * (tenders are not settled).
     */
    val paid: Boolean,
)

@Serializable
data class CustomerRef(
    val id: String,
    val displayName: String,
    val currency: String = "USD",
    val creditHold: Boolean = false,
    val phoneE164: String? = null,
    val email: String? = null,
)

@Serializable
data class ParkedCartRef(
    val cartId: String,
    val label: String,
    val currency: String,
    val subtotalCents: Long,
)

@Serializable
data class LiveRailIntent(
    val intentId: String,
    val mode: String,
    val amountCents: Long,
    val settled: Boolean,
)

/** Fake/Live can force rail failure before checkout. */
class LiveRailException(message: String) : Exception(message)
