package co.zw.nissangtr.customer.rpc

/** Mirrors `public.currency_code`. */
enum class CurrencyCode(val rpcValue: String) {
    USD("USD"),
    ZIG("ZIG"),
}

/** Mirrors `public.fulfillment_mode`. */
enum class FulfillmentMode(val rpcValue: String) {
    IMMEDIATE("immediate"),
    DISPATCH("dispatch"),
}

/** Mirrors `public.contipay_method` (subset used by storefront). */
enum class ContipayMethod(val rpcValue: String) {
    ECOCASH("ecocash"),
    CARD("card"),
}

/** Mirrors `public.paynow_method` (subset used by storefront). */
enum class PaynowMethod(val rpcValue: String) {
    ECOCASH("ecocash"),
    CARD("card"),
}

data class CartSummary(
    val id: String,
    val currency: CurrencyCode,
    val fulfillmentMode: FulfillmentMode,
    val status: String = "open",
    val lines: List<CartLineSummary> = emptyList(),
)

data class CartLineSummary(
    val id: String,
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
    val oemPartNumber: String? = null,
)

/** Shape from [RpcNames.GET_CUSTOMER_ORDER] JSONB. */
data class CustomerOrder(
    val invoiceId: String,
    val documentNumber: String?,
    val docType: String,
    val status: String,
    val fulfillmentMode: FulfillmentMode,
    val currency: CurrencyCode,
    val exchangeRateApplied: Double,
    val subtotal: Double,
    val total: Double,
    val amountPaid: Double,
    val amountOpen: Double,
    val cartId: String?,
    val postedAt: String?,
    val pickListStatus: String?,
    val deliveryNoteStatus: String?,
)

data class InvoiceSummary(
    val id: String,
    val documentNumber: String?,
    val status: String,
    val currency: CurrencyCode,
    val total: Double,
    val amountPaid: Double,
)

data class GarageVehicle(
    val id: String,
    val make: String?,
    val model: String?,
    val generation: String?,
    val engine: String?,
    val vin: String?,
    val isPrimary: Boolean,
)

data class GarageVehicleInput(
    val id: String? = null,
    val make: String? = null,
    val model: String? = null,
    val generation: String? = null,
    val engine: String? = null,
    val vin: String? = null,
    val isPrimary: Boolean = false,
)

/** Intent create result — no PSP crypto; settle stays webhook/service_role. */
data class PaymentIntentResult(
    val intentId: String,
    val provider: String,
)
