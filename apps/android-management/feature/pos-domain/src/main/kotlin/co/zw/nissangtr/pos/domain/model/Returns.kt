package co.zw.nissangtr.pos.domain.model

/*
 * Returns, old cores, warranty claims and stock by branch (Blueprint §10, phase 6). Sales staff prepare
 * a return; an approver (own sign-in, password for one call, or badge) posts it. Values always come
 * from the posted sale on the server, never from the tablet.
 */

/** A posted sale with what can still come back per line (`get_pos_invoice_detail`). */
data class InvoiceDetail(
    val id: String,
    val documentNumber: String?,
    val customerId: String?,
    val total: Money,
    val amountPaid: Money,
    val postedAt: String?,
    val tillSessionId: String?,
    val lines: List<InvoiceLine>,
) {
    val parts: List<InvoiceLine> get() = lines.filterNot { it.isCore }
    val cores: List<InvoiceLine> get() = lines.filter { it.isCore }

    /** A whole-sale refund only while nothing has come back yet: otherwise it would pay twice. */
    val untouched: Boolean get() = parts.isNotEmpty() && lines.all { it.returnableQty >= it.qty }
}

data class InvoiceLine(
    val id: String,
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String?,
    val uomId: String,
    val qty: Double,
    val unitPrice: Money,
    val lineTotal: Money,
    val isCore: Boolean,
    val returnableQty: Double,
) {
    /** The value of [returnQty] of this line, discounts included (pro rata of the line total). */
    fun valueOf(returnQty: Double): Money =
        if (qty <= 0.0) Money.zero(lineTotal.currency) else Money(Math.round(lineTotal.minor * returnQty / qty), lineTotal.currency)
}

/** What the customer gets for the parts that come back (`pos_return_resolution`). */
enum class ReturnResolution(val rpcValue: String) {
    CashRefund("cash_refund"),
    CreditNote("credit_note"),
    StoreCredit("store_credit"),
    Replacement("replacement"),
    Warranty("warranty"),
}

enum class ReturnCondition(val rpcValue: String) { Sealed("sealed"), Unopened("unopened"), Opened("opened"), Damaged("damaged"), Defective("defective") }

data class ReturnLine(val invoiceLineId: String, val qty: Double, val condition: ReturnCondition)

/** A return case before a sales person drafts it (`create_pos_return_case`). */
data class ReturnDraft(
    val invoiceId: String,
    val resolution: ReturnResolution,
    val reasonCode: String,
    val notes: String?,
    val lines: List<ReturnLine>,
    val tillSessionId: String?,
)

/** Why [resolution] cannot be chosen for [invoice], or null. Same rules as the server. */
fun ReturnResolution.blockedFor(invoice: InvoiceDetail, lineCount: Int, tillOpen: Boolean): String? = when {
    (this == ReturnResolution.CreditNote || this == ReturnResolution.StoreCredit) && invoice.customerId == null -> "named_customer"
    this == ReturnResolution.CashRefund && !tillOpen && invoice.tillSessionId == null -> "till_closed"
    this == ReturnResolution.CashRefund && invoice.amountPaid.minor <= 0 -> "nothing_paid"
    this == ReturnResolution.Warranty && lineCount != 1 -> "one_part"
    else -> null
}

enum class CoreResolution(val rpcValue: String) { CashRefund("cash_refund"), AccountCredit("account_credit"), StoreCredit("store_credit") }

data class CoreReturnInput(
    val invoiceId: String,
    val coreLineId: String,
    val qty: Double,
    val resolution: CoreResolution,
    val reasonCode: String,
    val tillSessionId: String?,
    val notes: String?,
)

data class WarrantyClaim(
    val id: String,
    val documentNumber: String?,
    /** open → approved | rejected → closed. */
    val status: String,
    val resolution: String?,
    val invoiceId: String?,
    val invoiceNumber: String?,
    val stockItemId: String?,
    val oemPartNumber: String?,
    val serialNumber: String?,
    val notes: String?,
    val rejectReason: String?,
    val createdAt: String,
    val decidedAt: String?,
)

data class WarrantySerial(val id: String, val serialNumber: String, val stockItemId: String, val oemPartNumber: String?)

/** A manager's decision on an open claim. A credit is valued at the sold price on the server. */
sealed interface WarrantyDecision {
    /** [uomId] null: the unit of the sold line for that part. */
    data class Replace(val qty: Double, val uomId: String?) : WarrantyDecision
    data class Credit(val qty: Double) : WarrantyDecision
    data object TakeBack : WarrantyDecision
    data class Reject(val reason: String) : WarrantyDecision
}

/** One part at one branch (`list_pos_stock_availability`). */
data class BranchStock(
    val warehouseId: String,
    val code: String,
    val name: String,
    val onHand: Double,
    val reserved: Double,
    val available: Double,
    val incoming: Double,
)

/** `warranty_claim_resolution` of an approval; a rejection goes through its own call. */
val WarrantyDecision.rpcResolution: String
    get() = when (this) {
        is WarrantyDecision.Replace -> "replacement"
        is WarrantyDecision.Credit -> "credit_note"
        WarrantyDecision.TakeBack -> "return_only"
        is WarrantyDecision.Reject -> "reject_only"
    }
