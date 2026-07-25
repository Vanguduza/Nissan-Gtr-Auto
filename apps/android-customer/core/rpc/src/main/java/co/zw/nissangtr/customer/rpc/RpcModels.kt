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
    /**
     * Non-terminal delivery job on this invoice (`pending`|`dispatched`), prefers dispatched.
     * Owner path: [RpcNames.GET_DELIVERY_TRACK_POINT] with this id (no share token).
     */
    val activeDeliveryJobId: String? = null,
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

/** Mirrors `chat_thread_kind`. */
enum class ChatThreadKind(val rpcValue: String) {
    SUPPORT("support"),
    PARTS("parts"),
}

/** Mirrors `chat_thread_status`. */
enum class ChatThreadStatus(val rpcValue: String) {
    OPEN("open"),
    ASSIGNED("assigned"),
    CLOSED("closed"),
}

/** Mirrors `chat_sender_kind`. */
enum class ChatSenderKind(val rpcValue: String) {
    CUSTOMER("customer"),
    STAFF("staff"),
    SYSTEM("system"),
}

data class ChatThread(
    val id: String,
    val customerUserId: String = "",
    val customerId: String? = null,
    val kind: ChatThreadKind,
    val status: ChatThreadStatus,
    val subject: String? = null,
    val assignedTo: String? = null,
    val lastMessageAt: String? = null,
    val createdAt: String = "",
)

data class ChatMessage(
    val id: String,
    val threadId: String,
    val senderUserId: String,
    val senderKind: ChatSenderKind,
    val body: String,
    val createdAt: String,
)

data class StartChatThreadInput(
    val kind: ChatThreadKind = ChatThreadKind.SUPPORT,
    val subject: String? = null,
    val body: String? = null,
)

fun ChatThread.previewLabel(): String {
    val sub = subject?.trim()
    if (!sub.isNullOrEmpty()) return sub
    return if (kind == ChatThreadKind.PARTS) "Parts inquiry" else "Support"
}

/**
 * Single last-known GPS + ETA from [RpcNames.GET_DELIVERY_TRACK_POINT].
 * Never a historical trail — RPC returns at most one row for `dispatched` jobs.
 */
data class DeliveryTrackPoint(
    val deliveryJobId: String,
    val lat: Double,
    val lng: Double,
    val recordedAt: String,
    val etaAt: String?,
    val etaSeconds: Int?,
    val status: String,
)

/** Formats ETA for customer UI (mirrors web `formatEtaLabel`). */
fun DeliveryTrackPoint.etaLabel(): String? {
    if (!etaAt.isNullOrBlank()) return etaAt
    val secs = etaSeconds ?: return null
    if (secs < 0) return null
    val mins = (secs + 30) / 60
    if (mins < 1) return "Less than a minute"
    if (mins < 60) return "About $mins min"
    val h = mins / 60
    val m = mins % 60
    return if (m == 0) "About $h h" else "About $h h $m min"
}

data class WishlistItem(
    val id: String,
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String? = null,
    val notifyWhenInStock: Boolean = false,
    val createdAt: String? = null,
)

data class CompareItem(
    val id: String,
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String? = null,
    val createdAt: String? = null,
)

enum class ProductReviewStatus(val rpcValue: String) {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
}

data class ProductReview(
    val id: String,
    val stockItemId: String,
    val oemPartNumber: String? = null,
    val description: String? = null,
    val rating: Int,
    val body: String = "",
    val status: ProductReviewStatus = ProductReviewStatus.PENDING,
    val createdAt: String? = null,
) {
    val label: String
        get() {
            val oem = oemPartNumber ?: "Part"
            val d = description?.trim()
            return if (!d.isNullOrEmpty()) "$oem · $d" else oem
        }
}

data class ProductReviewStats(
    val stockItemId: String,
    val avgRating: Double,
    val reviewCount: Int,
)
