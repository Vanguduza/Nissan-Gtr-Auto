package co.zw.nissangtr.pos.domain.model

/** Payment methods the POS settles with (`payment_tender`). Offline sales are cash-only. */
enum class Tender(val rpcValue: String) {
    Cash("cash"),
    Bank("bank"),
    EcoCash("ecocash"),
    StoreCredit("store_credit"),
    /** Receipt only: settled by the provider's webhook against a reserved order. */
    Paynow("paynow"),
    ContiPay("contipay"),
    /** Receipt only: charged to the customer's account (`checkout_pos_cart_on_account`). */
    OnAccount("account"),
}

/** Tenders the one-step checkout dialog offers (the others come from reserve-first checkout). */
val Tender.counter: Boolean get() = this == Tender.Cash || this == Tender.Bank || this == Tender.EcoCash || this == Tender.StoreCredit

data class TenderLine(val tender: Tender, val amount: Money)

data class ReceiptContacts(val email: String?, val whatsappE164: String?)

/**
 * A completed sale as the receipt shows it. Amounts are the server's; [change] is the only figure
 * the till works out itself, from cash handed over (it is never posted — tenders equal the total).
 */
data class Receipt(
    val invoiceId: String,
    val documentNumber: String?,
    val lines: List<CartLine>,
    val subtotal: Money,
    val discount: Money,
    val total: Money,
    val tenders: List<TenderLine>,
    val cashGiven: Money?,
    val change: Money?,
    val customerName: String?,
    val vehicleLabel: String?,
    val operatorName: String?,
    val issuedAtIso: String,
    /** Queued offline: the invoice number is assigned when the outbox replays (§10.12). */
    val offline: Boolean = false,
)

/** Offline outbox counts. [conflicts] are replays the server refused (price drift, stock) for review. */
data class OfflineSyncStatus(val pending: Int, val conflicts: Int, val synced: Int = 0)

/** A cash sale written to the encrypted outbox under a client-generated id. */
data class OfflineQueued(val clientSaleId: String, val soldAtIso: String, val status: OfflineSyncStatus)

enum class CompanionStatus { Open, Claimed, Revoked, Expired }

/** A companion-phone pairing bound to one server cart. */
data class CompanionSession(
    val sessionId: String,
    val cartId: String,
    val pairingCode: String,
    val expiresAtIso: String,
    val status: CompanionStatus,
) {
    /** The phone can still claim (open) or is scanning (claimed). */
    val live: Boolean get() = status == CompanionStatus.Open || status == CompanionStatus.Claimed
}

/** This device as the scanner for another till's sale (the phone half of the pairing). */
data class ScannerLink(
    val sessionId: String,
    val cartId: String,
    /** Most recent scans first, for the operator's own reassurance. */
    val scans: List<String> = emptyList(),
    val busy: Boolean = false,
)

enum class ReceiptPaper { Thermal80, A4 }

enum class CustomerKind { Individual, Business }

data class Customer(
    val id: String,
    val displayName: String,
    val kind: CustomerKind,
    val businessName: String?,
    val email: String?,
    val phoneE164: String?,
    val whatsappE164: String?,
)

data class CustomerDraft(
    val kind: CustomerKind,
    val displayName: String,
    val businessName: String?,
    val email: String?,
    val phoneE164: String?,
    val whatsappE164: String?,
)

data class GarageVehicle(
    val id: String,
    val modelSlug: String?,
    val model: String?,
    val generation: String?,
    val chassisCode: String?,
    val engine: String?,
    val isPrimary: Boolean,
) {
    fun selection(): VehicleSelection? {
        val slug = modelSlug ?: return null
        val chassis = chassisCode ?: return null
        val eng = engine ?: return null
        return VehicleSelection(slug, model ?: slug, generation ?: chassis, chassis, eng)
    }

    val label: String get() = listOfNotNull(model, chassisCode, engine).joinToString(" ")
}

data class ParkedSale(
    val id: String,
    val documentNumber: String?,
    val updatedAt: String?,
    val total: Money,
    val lineCount: Int,
)

data class Quotation(
    val id: String,
    val documentNumber: String?,
    val status: String,
    val validUntil: String?,
    val total: Money,
    val lineCount: Long,
    val sentChannel: String?,
)

enum class QuoteChannel(val rpcValue: String) { Print("print"), Email("email"), WhatsApp("whatsapp") }

data class InvoiceSummary(
    val id: String,
    val documentNumber: String?,
    val customerName: String?,
    val total: Money,
    val postedAt: String?,
    val vehicleLabel: String?,
)

data class ManagerCredentials(val identifier: String, val password: String, val notes: String?)

/** Actions that need a manager to sign in for this one action (owner decision D4). */
sealed interface ApprovalRequest {
    data class Discount(val percent: Double) : ApprovalRequest
    data class PriceOverride(val lineId: String, val unitPrice: Double) : ApprovalRequest
    data object VoidSale : ApprovalRequest
    data class Refund(val invoice: InvoiceSummary) : ApprovalRequest

    /** Paid at the provider but the sale did not finish: post it against that money (recovery). */
    data class RepairPaidOrder(val orderId: String) : ApprovalRequest

    /** Drawer actions: they act on the till session, never on the sale. */
    sealed interface TillAction : ApprovalRequest { val sessionId: String }

    /** Cash leaving the drawer (cash out, petty cash, bank drop, cash refund). */
    data class CashOut(
        override val sessionId: String,
        val kind: CashMovementKind,
        val amount: Money,
        val reason: ReasonCode,
        val notes: String?,
    ) : TillAction

    /** Close a till whose blind count did not match the expected cash. */
    data class TillVariance(override val sessionId: String, val variance: Money, val reason: ReasonCode) : TillAction

    /** Give the open till to another staff member. */
    data class Handover(override val sessionId: String, val to: HandoverOperator) : TillAction
}

// ---------------------------------------------------------------- EPC browse

data class EpcVariant(val slug: String, val chassisCode: String, val engineCode: String?, val yearLabel: String?) {
    val label: String get() = listOfNotNull(chassisCode, engineCode, yearLabel).joinToString(" · ")
}

data class EpcSection(val slug: String, val name: String)

/** [id] is the full-catalogue diagram id that keys its R2 part shard and image. */
data class EpcDiagram(val slug: String, val title: String, val id: String? = null)

/** Why part of a live-catalogue diagram could not be shown (fail closed, never fixture data). */
enum class EpcMissing { Publishing, NotConnected, Unavailable }

data class EpcPart(
    val oemPartNumber: String,
    val name: String,
    val pncCode: String?,
    val refNo: String?,
    val qtyRequired: String?,
)

/**
 * A clickable callout on an exploded diagram. Boxes arrive either as 0–1 fractions of the image
 * or as pixels of the source image (`part_fitment.bbox_*`); [normalizedIn] resolves both.
 */
data class EpcHotspot(
    val oemPartNumber: String,
    val pncCode: String?,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {
    val oemKey: String get() = oemPartNumber.trim().uppercase()

    /**
     * The box as fractions of the drawn image, clipped to it. Pixel boxes need the image size
     * (stored on the diagram or read from the decoded image); returns null when it is unknown or
     * the box falls entirely outside the image, so a bad callout is never drawn over the wrong part.
     */
    fun normalizedIn(imageWidth: Int?, imageHeight: Int?): EpcBox? {
        if (!(width > 0.0) || !(height > 0.0) || x < 0.0 || y < 0.0) return null
        val fractions = x <= 1.0 && y <= 1.0 && width <= 1.0 && height <= 1.0
        val (w, h) = when {
            fractions -> 1.0 to 1.0
            imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0 -> imageWidth.toDouble() to imageHeight.toDouble()
            else -> return null
        }
        val left = x / w
        val top = y / h
        if (left >= 1.0 || top >= 1.0) return null
        return EpcBox(left, top, minOf(width / w, 1.0 - left), minOf(height / h, 1.0 - top))
    }
}

/** Fractions (0–1) of the drawn diagram image. */
data class EpcBox(val left: Double, val top: Double, val width: Double, val height: Double)

data class EpcDiagramDetail(
    val diagram: EpcDiagram,
    val imageUrl: String?,
    val parts: List<EpcPart>,
    val hotspots: List<EpcHotspot> = emptyList(),
    /** Source image size when the catalogue stores it; otherwise the decoded image decides. */
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    /** Set when the image or the parts list is not available from the live catalogue. */
    val missing: EpcMissing? = null,
)

/** Diagram image bytes as fetched for [url]; [bytes] is null when the download failed. */
class EpcImage(val url: String, val bytes: ByteArray?) {
    override fun equals(other: Any?): Boolean =
        other is EpcImage && other.url == url && (other.bytes?.contentEquals(bytes) ?: (bytes == null))

    override fun hashCode(): Int = 31 * url.hashCode() + (bytes?.contentHashCode() ?: 0)
}
