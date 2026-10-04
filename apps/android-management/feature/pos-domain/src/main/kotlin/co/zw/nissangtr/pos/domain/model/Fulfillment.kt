package co.zw.nissangtr.pos.domain.model

/*
 * Getting a part to the customer when it is not on this shelf (phase 7, `pos_fulfillment_requests`):
 * hold it here, hold it at another branch for collection there, bring it here by branch transfer, or
 * back-order it. A hold goes with the current sale and becomes ready with its invoice when that sale
 * is paid; a transfer becomes ready when the warehouse posts it.
 */

enum class FulfillmentKind(val rpcValue: String) {
    CustomerCollection("customer_collection"),
    AlternatePickup("alternate_pickup"),
    BranchTransfer("branch_transfer"),
    Backorder("backorder"),
    ;

    companion object {
        fun of(raw: String): FulfillmentKind = entries.firstOrNull { it.rpcValue == raw } ?: Backorder
    }
}

enum class FulfillmentStep(val rpcValue: String) { SendTransfer("approve"), MarkReady("ready"), HandOver("collect"), Release("cancel") }

data class FulfillmentRequest(
    val id: String,
    val documentNumber: String?,
    val kind: FulfillmentKind,
    /** requested | reserved | awaiting_transfer_approval | ready | collected | cancelled | rejected */
    val status: String,
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String?,
    val qty: Double,
    val sourceName: String?,
    val destinationName: String?,
    val cartId: String?,
    val invoiceId: String?,
    val expiresAt: String?,
    val createdAt: String,
) {
    val active: Boolean get() = status !in setOf("collected", "cancelled", "rejected")

    /** The next steps the counter may take, in the server's own rules. */
    val steps: List<FulfillmentStep>
        get() = buildList {
            if (kind == FulfillmentKind.BranchTransfer && status == "reserved") add(FulfillmentStep.SendTransfer)
            // Holds become ready when their sale is paid (the invoice links then); only a back-order is marked ready by hand.
            if (kind == FulfillmentKind.Backorder && status == "requested") add(FulfillmentStep.MarkReady)
            if (status == "ready" && kind != FulfillmentKind.Backorder && (invoiceId != null || kind == FulfillmentKind.BranchTransfer)) add(FulfillmentStep.HandOver)
            // A paid hold is handed over (or returned), never just released.
            if (active && status != "awaiting_transfer_approval" && !(status == "ready" && invoiceId != null)) add(FulfillmentStep.Release)
        }
}

data class FulfillmentDraft(
    val kind: FulfillmentKind,
    val stockItemId: String,
    val qty: Double,
    val sourceWarehouseId: String?,
    val destinationWarehouseId: String?,
    val customerId: String?,
    val cartId: String?,
    val holdMinutes: Int = 120,
)
