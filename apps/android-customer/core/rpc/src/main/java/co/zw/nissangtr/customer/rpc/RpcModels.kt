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
    /** True when this line is the core-charge / deposit sibling (parent–child cart split). */
    val isCoreDeposit: Boolean = false,
    /** Unit price USD when known (fake / browse); null live until list cart exposes amounts. */
    val unitPriceUsd: Double? = null,
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

/** Human-readable "make · model · generation · engine" summary, falling back to VIN/id. */
fun GarageVehicle.summaryLabel(): String =
    listOfNotNull(make, model, generation, engine)
        .joinToString(" · ")
        .ifBlank { vin?.let { "VIN $it" } ?: id }

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

/** Own-row shipping address — mirrors `customer_addresses` + web `CustomerAddressRow`. */
data class CustomerAddress(
    val id: String,
    val label: String,
    val line1: String,
    val line2: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val country: String = "Zimbabwe",
    val isDefault: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    /** Human one-liner for list / checkout chips. */
    fun summaryLabel(): String {
        val parts = listOfNotNull(
            label.takeIf { it.isNotBlank() },
            line1,
            city,
            province,
        )
        return parts.joinToString(" · ").ifBlank { id }
    }

    /** Lat/lng encoded in line2 as `#gtr_geo:lat,lng` (no geo columns on table yet). */
    fun geoLatLng(): Pair<Double, Double>? = AddressGeo.parse(line2)
}

data class CustomerAddressInput(
    val id: String? = null,
    val label: String = "",
    val line1: String,
    val line2: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val country: String = "Zimbabwe",
    val isDefault: Boolean = false,
    /** Optional map pick — stored in line2 via [AddressGeo.embed] when set. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * Encode/decode map coordinates in `customer_addresses.line2` until a geo migration lands.
 * User apartment text stays above the `#gtr_geo:` marker.
 */
object AddressGeo {
    private val GEO = Regex("""#gtr_geo:(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")

    fun parse(line2: String?): Pair<Double, Double>? {
        val m = GEO.find(line2.orEmpty()) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    fun strip(line2: String?): String? {
        val raw = line2?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val cleaned = GEO.replace(raw, "").trim()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        return cleaned.takeIf { it.isNotEmpty() }
    }

    fun embed(line2: String?, latitude: Double?, longitude: Double?): String? {
        val base = strip(line2)
        if (latitude == null || longitude == null) return base
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            "latitude/longitude out of range"
        }
        val tag = "#gtr_geo:$latitude,$longitude"
        return if (base.isNullOrBlank()) tag else "$base\n$tag"
    }
}

/** Mirrors web `LoyaltyBalance` / `get_loyalty_balance` row. */
data class LoyaltyBalance(
    val customerId: String,
    val pointsBalance: Double,
    val currency: String = "USD",
    val liabilityPerPoint: Double = 0.0,
    val estimatedLiability: Double = 0.0,
)

/** Line payload for [RpcNames.POST_CUSTOMER_RETURN_CREDIT_NOTE] — prices forced server-side. */
data class ReturnCreditNoteLine(
    val stockItemId: String,
    val uomId: String,
    val qty: Double,
)

/** Kit component row — mirrors web `KitListItem.components`. */
data class KitComponent(
    val oem: String,
    val name: String,
    val qty: Double,
)

/** Mirrors web `KitListItem` / PostgREST `item_kits` browse. */
data class KitListItem(
    val kitId: String,
    val stockItemId: String,
    val oem: String,
    val name: String,
    val sellMode: String,
    val components: List<KitComponent> = emptyList(),
)
