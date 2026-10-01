package co.zw.nissangtr.pos.domain.model

/** Payment methods the POS settles with (`payment_tender`). Offline sales are cash-only. */
enum class Tender(val rpcValue: String) {
    Cash("cash"),
    Bank("bank"),
    EcoCash("ecocash"),
    StoreCredit("store_credit"),
}

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
}

// ---------------------------------------------------------------- EPC browse

data class EpcVariant(val slug: String, val chassisCode: String, val engineCode: String?, val yearLabel: String?) {
    val label: String get() = listOfNotNull(chassisCode, engineCode, yearLabel).joinToString(" · ")
}

data class EpcSection(val slug: String, val name: String)

data class EpcDiagram(val slug: String, val title: String)

data class EpcPart(
    val oemPartNumber: String,
    val name: String,
    val pncCode: String?,
    val refNo: String?,
    val qtyRequired: String?,
)

data class EpcDiagramDetail(val diagram: EpcDiagram, val imageUrl: String?, val parts: List<EpcPart>)
